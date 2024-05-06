package com.android.example.viewbinding;

import android.os.Bundle;
import androidx.annotation.Nullable;
import android.app.Activity;
import com.android.example.viewbinding.databinding.ActivityMainBinding;

class MainActivity extends Activity {
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
  }
}