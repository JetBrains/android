package com.android.example.appwithdatabinding;

import android.app.Activity;
import android.os.Bundle;

import com.android.example.appwithdatabinding.databinding.ActivityResAltBinding;

public class ResAltActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityResAltBinding binding = ActivityResAltBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }
}
