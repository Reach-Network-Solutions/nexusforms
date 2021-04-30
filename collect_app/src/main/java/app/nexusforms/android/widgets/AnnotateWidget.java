/*
 * Copyright (C) 2012 University of Washington
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

package app.nexusforms.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.Button;

import app.nexusforms.android.BuildConfig;
import app.nexusforms.android.R;

import app.nexusforms.android.activities.DrawActivity;
import app.nexusforms.android.formentry.questions.QuestionDetails;
import app.nexusforms.android.formentry.questions.WidgetViewUtils;
import app.nexusforms.android.listeners.PermissionListener;
import app.nexusforms.android.utilities.Appearances;
import app.nexusforms.android.utilities.ApplicationConstants;
import app.nexusforms.android.utilities.ContentUriProvider;
import app.nexusforms.android.utilities.FileUtils;
import app.nexusforms.android.utilities.MediaUtils;
import app.nexusforms.android.utilities.QuestionMediaManager;
import app.nexusforms.android.widgets.interfaces.ButtonClickListener;
import app.nexusforms.android.widgets.utilities.WaitingForDataRegistry;
import app.nexusforms.android.storage.StoragePathProvider;

import java.io.File;
import java.util.Locale;

import timber.log.Timber;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static app.nexusforms.android.formentry.questions.WidgetViewUtils.createSimpleButton;

/**
 * Image widget that supports annotations on the image.
 *
 * @author BehrAtherton@gmail.com
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
@SuppressLint("ViewConstructor")
public class AnnotateWidget extends BaseImageWidget implements ButtonClickListener {

    Button captureButton;
    Button chooseButton;
    Button annotateButton;

    public AnnotateWidget(Context context, QuestionDetails prompt, QuestionMediaManager questionMediaManager, WaitingForDataRegistry waitingForDataRegistry) {
        super(context, prompt, questionMediaManager, waitingForDataRegistry, new MediaUtils());
        imageClickHandler = new DrawImageClickHandler(DrawActivity.OPTION_ANNOTATE, ApplicationConstants.RequestCodes.ANNOTATE_IMAGE, R.string.annotate_image);
        imageCaptureHandler = new ImageCaptureHandler();
        setUpLayout();
        addCurrentImageToLayout();
        adjustAnnotateButtonAvailability();
        addAnswerView(answerLayout, WidgetViewUtils.getStandardMargin(context));
    }

    @Override
    protected void setUpLayout() {
        super.setUpLayout();
        captureButton = WidgetViewUtils.createSimpleButton(getContext(), R.id.capture_image, questionDetails.isReadOnly(), getContext().getString(R.string.capture_image), getAnswerFontSize(), this);

        chooseButton = WidgetViewUtils.createSimpleButton(getContext(), R.id.choose_image, questionDetails.isReadOnly(), getContext().getString(R.string.choose_image), getAnswerFontSize(), this);

        annotateButton = WidgetViewUtils.createSimpleButton(getContext(), R.id.markup_image, questionDetails.isReadOnly(), getContext().getString(R.string.markup_image), getAnswerFontSize(), this);

        annotateButton.setOnClickListener(v -> imageClickHandler.clickImage("annotateButton"));

        answerLayout.addView(captureButton);
        answerLayout.addView(chooseButton);
        answerLayout.addView(annotateButton);
        answerLayout.addView(errorTextView);

        hideButtonsIfNeeded();
        errorTextView.setVisibility(View.GONE);
    }

    @Override
    public Intent addExtrasToIntent(Intent intent) {
        intent.putExtra(DrawActivity.SCREEN_ORIENTATION, calculateScreenOrientation());
        return intent;
    }

    @Override
    protected boolean doesSupportDefaultValues() {
        return true;
    }

    @Override
    public void clearAnswer() {
        super.clearAnswer();
        annotateButton.setEnabled(false);

        // reset buttons
        captureButton.setText(getContext().getString(R.string.capture_image));
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        captureButton.setOnLongClickListener(l);
        chooseButton.setOnLongClickListener(l);
        annotateButton.setOnLongClickListener(l);
        super.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        captureButton.cancelLongPress();
        chooseButton.cancelLongPress();
        annotateButton.cancelLongPress();
    }

    @Override
    public void onButtonClick(int buttonId) {
        switch (buttonId) {
            case R.id.capture_image:
                getPermissionsProvider().requestCameraPermission((Activity) getContext(), new PermissionListener() {
                    @Override
                    public void granted() {
                        captureImage();
                    }

                    @Override
                    public void denied() {
                    }
                });
                break;
            case R.id.choose_image:
                imageCaptureHandler.chooseImage(R.string.annotate_image);
                break;
        }
    }

    private void adjustAnnotateButtonAvailability() {
        if (binaryName == null || imageView == null || imageView.getVisibility() == GONE) {
            annotateButton.setEnabled(false);
        }
    }

    private void hideButtonsIfNeeded() {
        if (getFormEntryPrompt().getAppearanceHint() != null
                && getFormEntryPrompt().getAppearanceHint().toLowerCase(Locale.ENGLISH).contains(Appearances.NEW)) {
            chooseButton.setVisibility(View.GONE);
        }
    }

    private int calculateScreenOrientation() {
        Bitmap bmp = null;
        if (imageView != null) {
            bmp = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        }

        return bmp != null && bmp.getHeight() > bmp.getWidth() ?
                SCREEN_ORIENTATION_PORTRAIT : SCREEN_ORIENTATION_LANDSCAPE;
    }

    private void captureImage() {
        errorTextView.setVisibility(View.GONE);
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        // We give the camera an absolute filename/path where to put the
        // picture because of bug:
        // http://code.google.com/p/android/issues/detail?id=1480
        // The bug appears to be fixed in Android 2.0+, but as of feb 2,
        // 2010, G1 phones only run 1.6. Without specifying the path the
        // images returned by the camera in 1.6 (and earlier) are ~1/4
        // the size. boo.

        try {
            Uri uri = ContentUriProvider.getUriForFile(getContext(),
                    BuildConfig.APPLICATION_ID + ".provider",
                    new File(new StoragePathProvider().getTmpImageFilePath()));
            // if this gets modified, the onActivityResult in
            // FormEntyActivity will also need to be updated.
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
            FileUtils.grantFilePermissions(intent, uri, getContext());
        } catch (IllegalArgumentException e) {
            Timber.e(e);
        }

        imageCaptureHandler.captureImage(intent, ApplicationConstants.RequestCodes.IMAGE_CAPTURE, R.string.annotate_image);
    }

    @Override
    public void setData(Object newImageObj) {
        super.setData(newImageObj);

        annotateButton.setEnabled(binaryName != null);
    }
}