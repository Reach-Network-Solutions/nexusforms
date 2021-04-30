package app.nexusforms.android.instancemanagement;

import android.util.Pair;

import androidx.annotation.NonNull;

import app.nexusforms.analytics.Analytics;
import app.nexusforms.android.R;
import app.nexusforms.android.application.Collect;
import app.nexusforms.android.database.DatabaseFormsRepository;
import app.nexusforms.android.database.DatabaseInstancesRepository;
import app.nexusforms.android.forms.Form;
import app.nexusforms.android.forms.FormsRepository;
import app.nexusforms.android.gdrive.GoogleAccountsManager;
import app.nexusforms.android.gdrive.GoogleApiProvider;
import app.nexusforms.android.gdrive.InstanceGoogleSheetsUploader;
import app.nexusforms.android.instances.Instance;
import app.nexusforms.android.instances.InstancesRepository;
import app.nexusforms.android.logic.PropertyManager;
import app.nexusforms.android.openrosa.OpenRosaHttpInterface;
import app.nexusforms.android.permissions.PermissionsProvider;
import app.nexusforms.android.preferences.keys.GeneralKeys;
import app.nexusforms.android.preferences.source.SettingsProvider;
import app.nexusforms.android.upload.InstanceServerUploader;
import app.nexusforms.android.upload.InstanceUploader;
import app.nexusforms.android.upload.UploadException;
import app.nexusforms.android.utilities.FileUtils;
import app.nexusforms.android.utilities.InstanceUploaderUtils;
import app.nexusforms.android.utilities.TranslationHandler;
import app.nexusforms.android.utilities.WebCredentialsUtils;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

import static app.nexusforms.android.analytics.AnalyticsEvents.CUSTOM_ENDPOINT_SUB;
import static app.nexusforms.android.analytics.AnalyticsEvents.SUBMISSION;
import static app.nexusforms.android.utilities.InstanceUploaderUtils.SPREADSHEET_UPLOADED_TO_GOOGLE_DRIVE;

public class InstanceSubmitter {

    private final Analytics analytics;
    private final FormsRepository formsRepository;
    private final InstancesRepository instancesRepository;
    private final GoogleAccountsManager googleAccountsManager;
    private final GoogleApiProvider googleApiProvider;
    private final PermissionsProvider permissionsProvider;
    private final SettingsProvider settingsProvider;

    public InstanceSubmitter(Analytics analytics, FormsRepository formsRepository, InstancesRepository instancesRepository,
                             GoogleAccountsManager googleAccountsManager, GoogleApiProvider googleApiProvider, PermissionsProvider permissionsProvider, SettingsProvider settingsProvider) {
        this.analytics = analytics;
        this.formsRepository = formsRepository;
        this.instancesRepository = instancesRepository;
        this.googleAccountsManager = googleAccountsManager;
        this.googleApiProvider = googleApiProvider;
        this.permissionsProvider = permissionsProvider;
        this.settingsProvider = settingsProvider;
    }

    public Pair<Boolean, String> submitUnsubmittedInstances() throws SubmitException {
        List<Instance> toUpload = getInstancesToAutoSend(!settingsProvider.getGeneralSettings().getString(GeneralKeys.KEY_AUTOSEND).equals("off"));
        return submitSelectedInstances(toUpload);
    }

    public Pair<Boolean, String> submitSelectedInstances(List<Instance> toUpload) throws SubmitException {
        if (toUpload.isEmpty()) {
            throw new SubmitException(SubmitException.Type.NOTHING_TO_SUBMIT);
        }

        String protocol = settingsProvider.getGeneralSettings().getString(GeneralKeys.KEY_PROTOCOL);

        InstanceUploader uploader;
        Map<String, String> resultMessagesByInstanceId = new HashMap<>();
        String deviceId = null;
        boolean anyFailure = false;

        if (protocol.equals(TranslationHandler.getString(Collect.getInstance(), R.string.protocol_google_sheets))) {
            if (permissionsProvider.isGetAccountsPermissionGranted()) {
                String googleUsername = googleAccountsManager.getLastSelectedAccountIfValid();
                if (googleUsername.isEmpty()) {
                    throw new SubmitException(SubmitException.Type.GOOGLE_ACCOUNT_NOT_SET);
                }
                googleAccountsManager.selectAccount(googleUsername);
                uploader = new InstanceGoogleSheetsUploader(googleApiProvider.getDriveApi(googleUsername), googleApiProvider.getSheetsApi(googleUsername));
            } else {
                throw new SubmitException(SubmitException.Type.GOOGLE_ACCOUNT_NOT_PERMITTED);
            }
        } else {
            OpenRosaHttpInterface httpInterface = Collect.getInstance().getComponent().openRosaHttpInterface();
            uploader = new InstanceServerUploader(httpInterface, new WebCredentialsUtils(settingsProvider.getGeneralSettings()), new HashMap<>(), settingsProvider);
            deviceId = new PropertyManager().getSingularProperty(PropertyManager.PROPMGR_DEVICE_ID);
        }

        for (Instance instance : toUpload) {
            try {
                String destinationUrl = uploader.getUrlToSubmitTo(instance, deviceId, null, null);
                if (protocol.equals(TranslationHandler.getString(Collect.getInstance(), R.string.protocol_google_sheets))
                        && !InstanceUploaderUtils.doesUrlRefersToGoogleSheetsFile(destinationUrl)) {
                    anyFailure = true;
                    resultMessagesByInstanceId.put(instance.getId().toString(), SPREADSHEET_UPLOADED_TO_GOOGLE_DRIVE);
                    continue;
                }
                String customMessage = uploader.uploadOneSubmission(instance, destinationUrl);
                resultMessagesByInstanceId.put(instance.getId().toString(), customMessage != null ? customMessage : TranslationHandler.getString(Collect.getInstance(), R.string.success));

                // If the submission was successful, delete the instance if either the app-level
                // delete preference is set or the form definition requests auto-deletion.
                // TODO: this could take some time so might be better to do in a separate process,
                // perhaps another worker. It also feels like this could fail and if so should be
                // communicated to the user. Maybe successful delete should also be communicated?
                if (InstanceUploaderUtils.shouldFormBeDeleted(formsRepository, instance.getJrFormId(), instance.getJrVersion(),
                        settingsProvider.getGeneralSettings().getBoolean(GeneralKeys.KEY_DELETE_AFTER_SEND))) {
                    new InstanceDeleter(new DatabaseInstancesRepository(), new DatabaseFormsRepository()).delete(instance.getId());
                }

                String action = protocol.equals(TranslationHandler.getString(Collect.getInstance(), R.string.protocol_google_sheets)) ?
                        "HTTP-Sheets auto" : "HTTP auto";
                String label = Collect.getFormIdentifierHash(instance.getJrFormId(), instance.getJrVersion());
                analytics.logEvent(SUBMISSION, action, label);

                String submissionEndpoint = settingsProvider.getGeneralSettings().getString(GeneralKeys.KEY_SUBMISSION_URL);
                if (!submissionEndpoint.equals(TranslationHandler.getString(Collect.getInstance(), R.string.default_odk_submission))) {
                    String submissionEndpointHash = FileUtils.getMd5Hash(new ByteArrayInputStream(submissionEndpoint.getBytes()));
                    analytics.logEvent(CUSTOM_ENDPOINT_SUB, submissionEndpointHash);
                }
            } catch (UploadException e) {
                Timber.d(e);
                anyFailure = true;
                resultMessagesByInstanceId.put(instance.getId().toString(),
                        e.getDisplayMessage());
            }
        }

        return new Pair<>(anyFailure, InstanceUploaderUtils.getUploadResultMessage(instancesRepository, Collect.getInstance(), resultMessagesByInstanceId));
    }

    /**
     * Returns instances that need to be auto-sent.
     */
    @NonNull
    private List<Instance> getInstancesToAutoSend(boolean isAutoSendAppSettingEnabled) {
        List<Instance> toUpload = new ArrayList<>();
        for (Instance instance : instancesRepository.getAllByStatus(Instance.STATUS_COMPLETE, Instance.STATUS_SUBMISSION_FAILED)) {
            if (shouldFormBeSent(formsRepository, instance.getJrFormId(), instance.getJrVersion(), isAutoSendAppSettingEnabled)) {
                toUpload.add(instance);
            }
        }

        return toUpload;
    }

    /**
     * Returns whether a form with the specified form_id should be auto-sent given the current
     * app-level auto-send settings. Returns false if there is no form with the specified form_id.
     * <p>
     * A form should be auto-sent if auto-send is on at the app level AND this form doesn't override
     * auto-send settings OR if auto-send is on at the form-level.
     *
     * @param isAutoSendAppSettingEnabled whether the auto-send option is enabled at the app level
     * @deprecated should be private what requires refactoring the whole class to make it testable
     */
    @Deprecated
    public static boolean shouldFormBeSent(FormsRepository formsRepository, String jrFormId, String jrFormVersion, boolean isAutoSendAppSettingEnabled) {
        Form form = formsRepository.getLatestByFormIdAndVersion(jrFormId, jrFormVersion);
        if (form == null) {
            return false;
        }
        return form.getAutoSend() == null ? isAutoSendAppSettingEnabled : Boolean.valueOf(form.getAutoSend());
    }
}