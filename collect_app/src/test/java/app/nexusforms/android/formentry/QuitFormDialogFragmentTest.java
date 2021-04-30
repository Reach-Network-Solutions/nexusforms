package app.nexusforms.android.formentry;

import android.content.DialogInterface;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import app.nexusforms.android.R;
import app.nexusforms.analytics.Analytics;

import app.nexusforms.android.formentry.saving.FormSaveViewModel;
import app.nexusforms.android.injection.config.AppDependencyModule;
import app.nexusforms.android.support.RobolectricHelpers;

import app.nexusforms.async.Scheduler;
import app.nexusforms.audiorecorder.recording.AudioRecorder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import app.nexusforms.analytics.Analytics;
import app.nexusforms.async.Scheduler;
import app.nexusforms.audiorecorder.recording.AudioRecorder;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class QuitFormDialogFragmentTest {

    private final FormSaveViewModel formSaveViewModel = mock(FormSaveViewModel.class);

    @Before
    public void setup() {
        RobolectricHelpers.overrideAppDependencyModule(new AppDependencyModule() {
            @Override
            public FormSaveViewModel.FactoryFactory providesFormSaveViewModelFactoryFactory(Analytics analytics, Scheduler scheduler, AudioRecorder audioRecorder) {
                return (owner, defaultArgs) -> new ViewModelProvider.Factory() {

                    @NonNull
                    @Override
                    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                        return (T) formSaveViewModel;
                    }
                };
            }
        });
    }

    @Test
    public void shouldShowCorrectButtons() {
        FragmentScenario<QuitFormDialogFragment> fragmentScenario = RobolectricHelpers.launchDialogFragment(QuitFormDialogFragment.class);
        fragmentScenario.onFragment(fragment -> {
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertThat(dialog.getButton(DialogInterface.BUTTON_POSITIVE).getVisibility(), equalTo(GONE));
            assertThat(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getVisibility(), equalTo(VISIBLE));
            assertThat(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getText(), equalTo(fragment.getString(R.string.do_not_exit)));
        });
    }

    @Test
    public void shouldShowCorrectTitle_whenNoFormIsLoaded() {
        FragmentScenario<QuitFormDialogFragment> fragmentScenario = RobolectricHelpers.launchDialogFragment(QuitFormDialogFragment.class);
        fragmentScenario.onFragment(fragment -> {
            AlertDialog dialog = (AlertDialog) fragment.getDialog();
            TextView dialogTitle = dialog.findViewById(R.id.alertTitle);
            assertThat(dialogTitle.getText().toString(), equalTo(fragment.getString(R.string.quit_application, fragment.getString(R.string.no_form_loaded))));
        });
    }

    @Test
    public void shouldShowCorrectTitle_whenFormIsLoaded() {
        when(formSaveViewModel.getFormName()).thenReturn("blah");

        FragmentScenario<QuitFormDialogFragment> fragmentScenario = RobolectricHelpers.launchDialogFragment(QuitFormDialogFragment.class);
        fragmentScenario.onFragment(fragment -> {
            AlertDialog dialog = (AlertDialog) fragment.getDialog();
            TextView dialogTitle = dialog.findViewById(R.id.alertTitle);
            assertThat(dialogTitle.getText().toString(), equalTo(fragment.getString(R.string.quit_application, "blah")));
        });
    }

    @Test
    public void dialogIsCancellable() {
        FragmentScenario<QuitFormDialogFragment> fragmentScenario = RobolectricHelpers.launchDialogFragment(QuitFormDialogFragment.class);
        fragmentScenario.onFragment(fragment -> {
            assertThat(fragment.isCancelable(), equalTo(true));
        });
    }

    @Test
    public void clickingCancel_shouldDismissTheDialog() {
        FragmentScenario<QuitFormDialogFragment> fragmentScenario = RobolectricHelpers.launchDialogFragment(QuitFormDialogFragment.class);
        fragmentScenario.onFragment(fragment -> {
            AlertDialog dialog = (AlertDialog) fragment.getDialog();
            assertTrue(dialog.isShowing());

            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
            assertFalse(dialog.isShowing());
        });
    }
}