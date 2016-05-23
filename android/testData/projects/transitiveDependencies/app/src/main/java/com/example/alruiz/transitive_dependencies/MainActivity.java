package com.example.alruiz.transitive_dependencies;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.util.Lists;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Lists.newArrayList();
    }
}
