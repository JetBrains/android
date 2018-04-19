package com.android.nullity;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public Color myMethod() {
        Color color = null;
        return color;
    }

    public Color myMethod1() {
        Color color = new Color();
        return color;
    }
}
