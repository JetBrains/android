package com.example.app.test;

import com.example.app.AndroidApp;
import com.google.common.truth.Truth;
import org.junit.Test;

public class AppTest {

    @Test
    public void test() {
        AndroidApp x = new AndroidApp();
        Truth.assertThat(x.getFromKmpLib()).startsWith(x.getFromAndroidLib());
    }
}
