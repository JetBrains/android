package com.android.tools.test.mylibrary;

import android.app.Activity;
import android.os.Bundle;

public class LibraryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
    }
}
