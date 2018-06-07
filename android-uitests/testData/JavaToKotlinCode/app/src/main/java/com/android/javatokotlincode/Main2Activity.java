package com.android.javatokotlincode;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Main2Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
    }


// --- START COPY HERE ---
    public int dummyFun(int a, int b) {
        int p = a - b;
        return p;
    }
// --- END COPY HERE ---
}
