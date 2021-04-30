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
import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.TimeData;
import org.javarosa.form.api.FormEntryPrompt;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import app.nexusforms.android.R;
import app.nexusforms.android.databinding.TimeWidgetAnswerBinding;

import app.nexusforms.android.formentry.questions.QuestionDetails;
import app.nexusforms.android.utilities.DateTimeUtils;
import app.nexusforms.android.widgets.interfaces.WidgetDataReceiver;
import app.nexusforms.android.widgets.utilities.DateTimeWidgetUtils;

@SuppressLint("ViewConstructor")
public class TimeWidget extends QuestionWidget implements WidgetDataReceiver {
    TimeWidgetAnswerBinding binding;

    private final DateTimeWidgetUtils widgetUtils;

    private LocalDateTime selectedTime;

    public TimeWidget(Context context, final QuestionDetails prompt, DateTimeWidgetUtils widgetUtils) {
        super(context, prompt);
        this.widgetUtils = widgetUtils;
    }

    @Override
    protected View onCreateAnswerView(Context context, FormEntryPrompt prompt, int answerFontSize) {
        binding = TimeWidgetAnswerBinding.inflate(((Activity) context).getLayoutInflater());

        if (prompt.isReadOnly()) {
            binding.layoutTimeWidget.setEnabled(false);
        } else {
            binding.layoutTimeWidget.getEditText().setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);

            binding.layoutTimeWidget.getEditText().setOnClickListener(v -> {
                DateTimeWidgetUtils.setWidgetWaitingForData(prompt.getIndex());
                widgetUtils.showTimePickerDialog(context, selectedTime);
            });

            binding.layoutTimeWidget.setEndIconOnClickListener(v -> {
                DateTimeWidgetUtils.setWidgetWaitingForData(prompt.getIndex());
                widgetUtils.showTimePickerDialog(context, selectedTime);
            });
        }
        binding.layoutTimeWidget.getEditText().setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);

        if (prompt.getAnswerValue() == null) {
            selectedTime = DateTimeUtils.getCurrentDateTime();
        } else {
            DateTime dateTime = new DateTime(getFormEntryPrompt().getAnswerValue().getValue());
            selectedTime = DateTimeUtils.getSelectedTime(dateTime.toLocalDateTime(), LocalDateTime.now());
            binding.layoutTimeWidget.getEditText().setText(DateTimeUtils.getTimeData(dateTime).getDisplayText());
        }

        return binding.getRoot();
    }

    @Override
    public void clearAnswer() {
        selectedTime = DateTimeUtils.getCurrentDateTime();
        binding.layoutTimeWidget.getEditText().setText(R.string.no_time_selected);
        widgetValueChanged();
    }

    @Override
    public IAnswerData getAnswer() {
        return binding.layoutTimeWidget.getEditText().getText().equals(getContext().getString(R.string.no_time_selected))
                ? null
                : new TimeData(selectedTime.toDateTime().toDate());
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        binding.layoutTimeWidget.setOnLongClickListener(l);
        binding.layoutTimeWidget.getEditText().setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        binding.layoutTimeWidget.cancelLongPress();
        binding.layoutTimeWidget.getEditText().cancelLongPress();
    }

    @Override
    public void setData(Object answer) {
        if (answer instanceof DateTime) {
            selectedTime = DateTimeUtils.getSelectedTime(((DateTime) answer).toLocalDateTime(), LocalDateTime.now());
            binding.layoutTimeWidget.getEditText().setText(new TimeData(selectedTime.toDate()).getDisplayText());
        }
    }
}