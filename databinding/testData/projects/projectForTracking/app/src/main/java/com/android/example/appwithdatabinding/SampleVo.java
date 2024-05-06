package com.android.example.appwithdatabinding;
import androidx.databinding.ObservableInt;
import androidx.databinding.ObservableField;
public class SampleVo {
    public final ObservableField<String> firstName = new ObservableField<>();
    public final ObservableInt randomInt = new ObservableInt(1);

    public String name;
}
