/*
 * Copyright (C) 2011 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package app.nexusforms.android.activities;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;

import app.nexusforms.android.R;

import app.nexusforms.android.activities.viewmodels.FormMapViewModel;
import app.nexusforms.android.preferences.keys.AdminKeys;
import app.nexusforms.android.preferences.screens.MapsPreferencesFragment;
import app.nexusforms.android.database.DatabaseInstancesRepository;
import app.nexusforms.android.forms.Form;
import app.nexusforms.android.forms.FormsRepository;
import app.nexusforms.android.geo.MapFragment;
import app.nexusforms.android.geo.MapPoint;
import app.nexusforms.android.geo.MapProvider;
import app.nexusforms.android.injection.DaggerUtils;
import app.nexusforms.android.instances.Instance;
import app.nexusforms.android.instances.InstancesRepository;
import app.nexusforms.android.provider.FormsProviderAPI;
import app.nexusforms.android.provider.InstanceProvider;
import app.nexusforms.android.provider.InstanceProviderAPI;
import app.nexusforms.android.utilities.ApplicationConstants;
import app.nexusforms.android.utilities.IconUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

/**
 * Show a map with points representing saved instances of the selected form.
 */
public class FormMapActivity extends BaseGeoMapActivity {

    public static final String MAP_CENTER_KEY = "map_center";
    public static final String MAP_ZOOM_KEY = "map_zoom";

    public static final String EXTRA_FORM_ID = "form_id";

    private FormMapViewModel viewModel;

    @Inject
    MapProvider mapProvider;

    @Inject
    FormsRepository formsRepository;

    private MapFragment map;

    public BottomSheetBehavior summarySheet;

    /**
     * Quick lookup of instance objects from map feature IDs.
     */
    final Map<Integer, FormMapViewModel.MappableFormInstance> instancesByFeatureId = new HashMap<>();

    /**
     * Points to be mapped. Note: kept separately from {@link #instancesByFeatureId} so we can
     * quickly zoom to bounding box.
     */
    private final List<MapPoint> points = new ArrayList<>();

    /**
     * True if the map viewport has been initialized, false otherwise.
     */
    private boolean viewportInitialized;

    @VisibleForTesting
    public ViewModelProvider.Factory viewModelFactory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DaggerUtils.getComponent(this).inject(this);

        Form form = formsRepository.get(getIntent().getLongExtra(EXTRA_FORM_ID, -1));

        if (viewModelFactory == null) { // tests set their factories directly
            viewModelFactory = new FormMapActivity.FormMapViewModelFactory(form, new DatabaseInstancesRepository());
        }

        viewModel = new ViewModelProvider(this, viewModelFactory).get(FormMapViewModel.class);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.instance_map_layout);
        setUpSummarySheet();

        TextView titleView = findViewById(R.id.form_title);
        titleView.setText(viewModel.getFormTitle());

        MapFragment mapToAdd = mapProvider.createMapFragment(getApplicationContext());

        if (mapToAdd != null) {
            mapToAdd.addTo(this, R.id.map_container, this::initMap, this::finish);
        } else {
            finish(); // The configured map provider is not available
        }
    }

    private void setUpSummarySheet() {
        summarySheet = BottomSheetBehavior.from(findViewById(R.id.submission_summary));
        summarySheet.setState(BottomSheetBehavior.STATE_HIDDEN);
        summarySheet.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN && viewModel.getSelectedSubmissionId() != -1) {
                    updateSubmissionMarker(viewModel.getSelectedSubmissionId(), getSubmissionStatusFor(viewModel.getSelectedSubmissionId()), false);
                    viewModel.setSelectedSubmissionId(-1);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (map == null) {
            // initMap() is called asynchronously, so map can be null if the activity
            // is stopped (e.g. by screen rotation) before initMap() gets to run.
            // In this case, preserve any provided instance state.
            if (previousState != null) {
                state.putAll(previousState);
            }
            return;
        }
        state.putParcelable(MAP_CENTER_KEY, map.getCenter());
        state.putDouble(MAP_ZOOM_KEY, map.getZoom());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateInstanceGeometry();
    }

    @Override
    public void onBackPressed() {
        if (summarySheet.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            summarySheet.setState(BottomSheetBehavior.STATE_HIDDEN);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressLint("MissingPermission") // Permission handled in Constructor
    public void initMap(MapFragment newMapFragment) {
        map = newMapFragment;

        findViewById(R.id.zoom_to_location).setOnClickListener(v ->
                map.zoomToPoint(map.getGpsLocation(), true));

        findViewById(R.id.zoom_to_bounds).setOnClickListener(v ->
                map.zoomToBoundingBox(points, 0.8, false));

        findViewById(R.id.layer_menu).setOnClickListener(v -> {
            MapsPreferencesFragment.showReferenceLayerDialog(this);
        });

        findViewById(R.id.new_instance).setOnClickListener(v -> {
            final Uri formUri = ContentUris.withAppendedId(FormsProviderAPI.FormsColumns.CONTENT_URI, viewModel.getFormId());
            startActivity(new Intent(Intent.ACTION_EDIT, formUri));
        });

        map.setGpsLocationEnabled(true);
        map.setGpsLocationListener(this::onLocationChanged);

        if (previousState != null) {
            restoreFromInstanceState(previousState);
        }

        map.setFeatureClickListener(this::onFeatureClicked);
        map.setClickListener(this::onClick);
        updateInstanceGeometry();

        if (viewModel.getSelectedSubmissionId() != -1) {
            onFeatureClicked(viewModel.getSelectedSubmissionId());
        }
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void onClick(MapPoint mapPoint) {
        if (summarySheet.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            summarySheet.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private void updateInstanceGeometry() {
        if (map == null) {
            return;
        }

        updateMapFeatures();

        if (!viewportInitialized && !points.isEmpty()) {
            map.zoomToBoundingBox(points, 0.8, false);
            viewportInitialized = true;
        }

        TextView statusView = findViewById(R.id.geometry_status);
        statusView.setText(getString(R.string.geometry_status, viewModel.getTotalInstanceCount(), points.size()));
    }

    /**
     * Clears the existing features on the map and places features for the current form's instances.
     */
    private void updateMapFeatures() {
        points.clear();
        map.clearFeatures();
        instancesByFeatureId.clear();

        List<FormMapViewModel.MappableFormInstance> instances = viewModel.getMappableFormInstances();
        for (FormMapViewModel.MappableFormInstance instance : instances) {
            MapPoint point = new MapPoint(instance.getLatitude(), instance.getLongitude());
            int featureId = map.addMarker(point, false, MapFragment.BOTTOM);

            updateSubmissionMarker(featureId, instance.getStatus(), featureId == viewModel.getSelectedSubmissionId());

            instancesByFeatureId.put(featureId, instance);
            points.add(point);
        }
    }

    private void updateSubmissionMarker(int featureId, String status, boolean enlarged) {
        int drawableId = getDrawableIdForStatus(status, enlarged);
        map.setMarkerIcon(featureId, drawableId);
    }

    /**
     * Zooms the map to the new location if the map viewport hasn't been initialized yet.
     */
    public void onLocationChanged(MapPoint point) {
        if (!viewportInitialized) {
            map.zoomToPoint(point, true);
            viewportInitialized = true;
        }
    }

    /**
     * Reacts to a tap on a feature by showing a submission summary.
     */
    public void onFeatureClicked(int featureId) {
        summarySheet.setState(BottomSheetBehavior.STATE_HIDDEN);

        if (!isSummaryForGivenSubmissionDisplayed(featureId)) {
            removeEnlargedMarkerIfExist(featureId);

            FormMapViewModel.MappableFormInstance mappableFormInstance = instancesByFeatureId.get(featureId);
            if (mappableFormInstance != null) {
                map.zoomToPoint(new MapPoint(mappableFormInstance.getLatitude(), mappableFormInstance.getLongitude()), map.getZoom(), true);
                updateSubmissionMarker(featureId, mappableFormInstance.getStatus(), true);
                setUpSummarySheetDetails(mappableFormInstance);
            }
            viewModel.setSelectedSubmissionId(featureId);
        }
    }

    private boolean isSummaryForGivenSubmissionDisplayed(int newSubmissionId) {
        return viewModel.getSelectedSubmissionId() == newSubmissionId && summarySheet.getState() != BottomSheetBehavior.STATE_HIDDEN;
    }

    protected void restoreFromInstanceState(Bundle state) {
        MapPoint mapCenter = state.getParcelable(MAP_CENTER_KEY);
        double mapZoom = state.getDouble(MAP_ZOOM_KEY);
        if (mapCenter != null) {
            map.zoomToPoint(mapCenter, mapZoom, false);
            viewportInitialized = true; // avoid recentering as soon as location is received
        }
    }

    private static int getDrawableIdForStatus(String status, boolean enlarged) {
        switch (status) {
            case Instance.STATUS_INCOMPLETE:
                return enlarged ? R.drawable.ic_room_blue_48dp : R.drawable.ic_room_blue_24dp;
            case Instance.STATUS_COMPLETE:
                return enlarged ? R.drawable.ic_room_deep_purple_48dp : R.drawable.ic_room_deep_purple_24dp;
            case Instance.STATUS_SUBMITTED:
                return enlarged ? R.drawable.ic_room_green_48dp : R.drawable.ic_room_green_24dp;
            case Instance.STATUS_SUBMISSION_FAILED:
                return enlarged ? R.drawable.ic_room_red_48dp : R.drawable.ic_room_red_24dp;
        }
        return R.drawable.ic_map_point;
    }

    private void setUpSummarySheetDetails(FormMapViewModel.MappableFormInstance mappableFormInstance) {
        setUpSubmissionSheetNameAndLastChangedDate(mappableFormInstance);
        setUpSummarySheetIcon(mappableFormInstance.getStatus());
        adjustSubmissionSheetBasedOnItsStatus(mappableFormInstance);

        summarySheet.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void setUpSubmissionSheetNameAndLastChangedDate(FormMapViewModel.MappableFormInstance mappableFormInstance) {
        ((TextView) findViewById(R.id.submission_name)).setText(mappableFormInstance.getInstanceName());
        String instanceLastStatusChangeDate = InstanceProvider.getDisplaySubtext(this, mappableFormInstance.getStatus(), mappableFormInstance.getLastStatusChangeDate());
        ((TextView) findViewById(R.id.status_text)).setText(instanceLastStatusChangeDate);
    }

    private void setUpSummarySheetIcon(String status) {
        ImageView statusImage = findViewById(R.id.status_icon);
        statusImage.setImageDrawable(IconUtils.getSubmissionSummaryStatusIcon(this, status));
        statusImage.setBackground(null);
    }

    private void adjustSubmissionSheetBasedOnItsStatus(FormMapViewModel.MappableFormInstance mappableFormInstance) {
        switch (mappableFormInstance.getClickAction()) {
            case DELETED_TOAST:
                String deletedTime = getString(R.string.deleted_on_date_at_time);
                String disabledMessage = new SimpleDateFormat(deletedTime,
                        Locale.getDefault()).format(viewModel.getDeletedDateOf(mappableFormInstance.getDatabaseId()));
                setUpInfoText(disabledMessage);
                break;
            case NOT_VIEWABLE_TOAST:
                setUpInfoText(getString(R.string.cannot_edit_completed_form));
                break;
            case OPEN_READ_ONLY:
                setUpOpenFormButton(false, mappableFormInstance.getDatabaseId());
                break;
            case OPEN_EDIT:
                boolean canEditSaved = settingsProvider.getAdminSettings().getBoolean(AdminKeys.KEY_EDIT_SAVED);
                setUpOpenFormButton(canEditSaved, mappableFormInstance.getDatabaseId());
                break;
        }
    }

    private void setUpOpenFormButton(boolean canEdit, long instanceId) {
        findViewById(R.id.info).setVisibility(View.GONE);
        Chip openFormButton = findViewById(R.id.openFormChip);
        openFormButton.setVisibility(View.VISIBLE);
        openFormButton.setText(canEdit ? R.string.review_data : R.string.view_data);
        openFormButton.setChipIcon(ContextCompat.getDrawable(this, canEdit ? R.drawable.ic_edit : R.drawable.ic_visibility));
        openFormButton.setOnClickListener(v -> {
            summarySheet.setState(BottomSheetBehavior.STATE_HIDDEN);
            startActivity(canEdit
                    ? getEditFormInstanceIntentFor(instanceId)
                    : getViewOnlyFormInstanceIntentFor(instanceId));
        });
    }

    private void setUpInfoText(String message) {
        findViewById(R.id.openFormChip).setVisibility(View.GONE);
        TextView infoText = findViewById(R.id.info);
        infoText.setVisibility(View.VISIBLE);
        infoText.setText(message);
    }

    private Intent getViewOnlyFormInstanceIntentFor(long instanceId) {
        Intent intent = getEditFormInstanceIntentFor(instanceId);
        intent.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.VIEW_SENT);
        return intent;
    }

    private Intent getEditFormInstanceIntentFor(long instanceId) {
        Uri uri = ContentUris.withAppendedId(InstanceProviderAPI.InstanceColumns.CONTENT_URI, instanceId);
        return new Intent(Intent.ACTION_EDIT, uri);
    }

    private void removeEnlargedMarkerIfExist(int newSubmissionId) {
        if (viewModel.getSelectedSubmissionId() != -1 && viewModel.getSelectedSubmissionId() != newSubmissionId) {
            updateSubmissionMarker(viewModel.getSelectedSubmissionId(), getSubmissionStatusFor(viewModel.getSelectedSubmissionId()), false);
        }
    }

    private String getSubmissionStatusFor(int submissionId) {
        return instancesByFeatureId.get(submissionId).getStatus();
    }

    /**
     * Build {@link FormMapViewModel} and its dependencies.
     */
    private class FormMapViewModelFactory implements ViewModelProvider.Factory {
        private final Form form;
        private final InstancesRepository instancesRepository;

        FormMapViewModelFactory(@NonNull Form form, InstancesRepository instancesRepository) {
            this.form = form;
            this.instancesRepository = instancesRepository;
        }

        @Override
        @NonNull
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new FormMapViewModel(form, instancesRepository);
        }
    }
}