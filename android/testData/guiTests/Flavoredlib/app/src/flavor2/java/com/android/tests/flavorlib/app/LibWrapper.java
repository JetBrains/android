package com.android.tests.flavorlib.app;

import com.android.tests.flavorlib.lib.flavor2.Lib;

import android.app.Activity;

/**
 */
public class LibWrapper {

    public static void handleTextView(Activity a) {
        Lib.handleTextView(a);
    }
}
