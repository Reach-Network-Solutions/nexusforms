/*
 * Copyright (C) 2009 University of Washington
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
import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

import app.nexusforms.android.R;

import app.nexusforms.android.activities.FormEntryActivity;
import app.nexusforms.android.databinding.WidgetSimpleInputLayoutBinding;
import app.nexusforms.android.databinding.WidgetSimpleStringInputBinding;
import app.nexusforms.android.formentry.questions.QuestionDetails;
import app.nexusforms.android.formentry.questions.WidgetViewUtils;

import timber.log.Timber;

/**
 * The most basic widget that allows for entry of any text.
 */
@SuppressLint("ViewConstructor")
public class StringWidget extends QuestionWidget {
    public final TextInputLayout answerTextInputLayout;
    public final TextInputEditText answerEditText;

    WidgetSimpleStringInputBinding binding;


    protected StringWidget(Context context, QuestionDetails questionDetails) {
        super(context, questionDetails);

        answerTextInputLayout = getAnswerEditText(questionDetails.isReadOnly() || this instanceof ExStringWidget, getFormEntryPrompt());
        answerEditText = binding.textInput;

        setUpLayout(context);
    }

    protected void setUpLayout(Context context) {
        setDisplayValueFromModel();
        addAnswerView(answerTextInputLayout, WidgetViewUtils.getStandardMargin(context));
    }

    @Override
    public void clearAnswer() {

        answerEditText.setText(null);
        widgetValueChanged();
    }

    @Override
    public IAnswerData getAnswer() {
        String answer = getAnswerText();
        return !answer.isEmpty() ? new StringData(answer) : null;
    }

    @NonNull
    public String getAnswerText() {
        return answerEditText.getText().toString();
    }

    @Override
    public void setFocus(Context context) {
        if (!questionDetails.isReadOnly()) {
            softKeyboardController.showSoftKeyboard(answerTextInputLayout);
            /*
             * If you do a multi-question screen after a "add another group" dialog, this won't
             * automatically pop up. It's an Android issue.
             *
             * That is, if I have an edit text in an activity, and pop a dialog, and in that
             * dialog's button's OnClick() I call edittext.requestFocus() and
             * showSoftInput(edittext, 0), showSoftinput() returns false. However, if the edittext
             * is focused before the dialog pops up, everything works fine. great.
             */
        } else {
            softKeyboardController.hideSoftKeyboard(answerTextInputLayout);
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        answerEditText.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        answerEditText.cancelLongPress();
    }

    /**
     * Registers all subviews except for the EditText to clear on long press. This makes it possible
     * to long-press to paste or perform other text editing functions.
     */
    @Override
    protected void registerToClearAnswerOnLongPress(FormEntryActivity activity, ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child.getId() == R.id.help_layout) {
                child.setId(getId());
                activity.registerForContextMenu(child);
            } else if (child instanceof ViewGroup) {
                registerToClearAnswerOnLongPress(activity, (ViewGroup) child);
            } else if (!(child instanceof EditText)) {
                child.setId(getId());
                activity.registerForContextMenu(child);
            }
        }
    }

    public void setDisplayValueFromModel() {
        String currentAnswer = getFormEntryPrompt().getAnswerText();

        if (currentAnswer != null) {
            answerEditText.setText(currentAnswer);
            Selection.setSelection(answerEditText.getText(), answerEditText.getText().toString().length());
        }
    }

    private TextInputLayout getAnswerEditText(boolean readOnly, FormEntryPrompt prompt) {
        /*TextInputLayout answerInputLayout = new TextInputLayout(getContext(), null, R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        answerInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        answerInputLayout.setBoxStrokeColor(ContextCompat.getColor(getContext(), R.color.outlined_stroke_color));
        answerInputLayout.setBoxCornerRadii(convertToDp(3), convertToDp(3), convertToDp(3), convertToDp(3));

        TextInputEditText answerEditText = new TextInputEditText(getContext());
        answerEditText.setId(View.generateViewId());
        answerEditText.setPadding(12,24,12,24);
        answerEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getAnswerFontSize());
        answerEditText.setKeyListener(new TextKeyListener(TextKeyListener.Capitalize.SENTENCES, false));

        // needed to make long read only text scroll
        answerEditText.setHorizontallyScrolling(false);
        answerEditText.setSingleLine(false);

        answerEditText.setBackground(null);
        answerEditText.setEnabled(!readOnly);
        answerEditText.setTextColor(themeUtils.getColorOnSurface());
        answerEditText.setFocusable(!readOnly);
        if (readOnly) {
            answerInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
        }


        answerEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                widgetValueChanged();
            }
        });

        QuestionDef questionDef = prompt.getQuestion();
        if (questionDef != null) {
            *//*
             * If a 'rows' attribute is on the input tag, set the minimum number of lines
             * to display in the field to that value.
             *
             * I.e.,
             * <input ref="foo" rows="5">
             *   ...
             * </input>
             *
             * will set the height of the EditText box to 5 rows high.
             *//*
            String height = questionDef.getAdditionalAttribute(null, "rows");
            if (height != null && height.length() != 0) {
                try {
                    int rows = Integer.parseInt(height);
                    answerEditText.setMinLines(rows);
                    answerEditText.setGravity(Gravity.TOP); // to write test starting at the top of the edit area
                } catch (Exception e) {
                    Timber.e("Unable to process the rows setting for the answerText field: %s", e.toString());
                }
            }
        }

        answerInputLayout.addView(answerEditText);*/

        binding = WidgetSimpleStringInputBinding.inflate(LayoutInflater.from(getContext()),
                null,
                false
        );
        binding.textInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getAnswerFontSize());
        binding.textInput.setKeyListener(new TextKeyListener(TextKeyListener.Capitalize.SENTENCES, false));

        // needed to make long read only text scroll
        binding.textInput.setHorizontallyScrolling(false);
        binding.textInput.setSingleLine(false);
        binding.textInput.setEnabled(!readOnly);
        binding.textInput.setTextColor(themeUtils.getColorOnSurface());
        binding.textInput.setFocusable(!readOnly);
        if (readOnly) {
            binding.layoutInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_NONE);
        }


        binding.textInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                widgetValueChanged();
            }
        });

        QuestionDef questionDef = prompt.getQuestion();
        if (questionDef != null) {
            /*
             * If a 'rows' attribute is on the input tag, set the minimum number of lines
                    * to display in the field to that value.
                    *
             * I.e.,
             * <input ref="foo" rows="5">
             *   ...
             * </input>
             *
             * will set the height of the EditText box to 5 rows high.
             */
            String height = questionDef.getAdditionalAttribute(null, "rows");
            if (height != null && height.length() != 0) {
                try {
                    int rows = Integer.parseInt(height);
                    binding.textInput.setMinLines(rows);
                    binding.textInput.setGravity(Gravity.TOP); // to write test starting at the top of the edit area
                } catch (Exception e) {
                    Timber.e("Unable to process the rows setting for the answerText field: %s", e.toString());
                }
            }
        }

        return binding.getRoot();
    }


    public int convertToDp(int input) { // Get the screen's density scale
        final float scale = getContext().getResources().getDisplayMetrics().density;
        // Convert the dps to pixels, based on density scale
        return (int) (input * scale + 0.5f);
    }
}