package com.android.example.appwithdatabinding;

import androidx.lifecycle.LiveData;
import androidx.databinding.ObservableField;

public class DummyVo {
    public String name;

    public String getLiveDataString() {
        return null;
    }

    public String getObservableFieldString() {
        return null;
    }

    public LiveData<DummyVo> getLiveData() {
        return null;
    }

    public ObservableField<DummyVo> getObservableField() {
        return null;
    }
}
