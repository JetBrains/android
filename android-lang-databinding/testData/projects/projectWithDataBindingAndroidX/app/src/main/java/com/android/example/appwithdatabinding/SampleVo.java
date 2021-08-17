package com.android.example.appwithdatabinding;

import android.view.View;
import androidx.lifecycle.LiveData;
import androidx.databinding.ObservableField;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public class SampleVo {
    public String name;

    public String getLiveDataString() {
        return null;
    }

    public String getObservableFieldString() {
        return null;
    }

    public LiveData<SampleVo> getLiveData() {
        return null;
    }

    public StateFlow<String> getStateFlowString() {
        return StateFlowKt.MutableStateFlow("");
    }

    public ObservableField<SampleVo> getObservableField() {
        return null;
    }

    public void saveView(View view) {}
}
