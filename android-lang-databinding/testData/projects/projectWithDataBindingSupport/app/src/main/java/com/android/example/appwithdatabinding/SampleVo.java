package com.android.example.appwithdatabinding;

import android.view.View;
import android.arch.lifecycle.LiveData;
import android.databinding.ObservableField;

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

    public ObservableField<SampleVo> getObservableField() {
        return null;
    }

    public void saveView(View view) {}
}
