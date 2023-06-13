package com.mycompany.myapp.subpackage;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.mycompany.myapp.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}