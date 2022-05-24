package com.android.example.appwithdatabinding;

import androidx.databinding.ObservableField;
import androidx.databinding.ObservableInt;
public class SampleVo {
    public final ObservableField<String> firstName = new ObservableField<>();
    public final ObservableInt randomInt = new ObservableInt(1);

    public String name;
}
