package com.example;

import android.app.Application;
import android.test.ApplicationTestCase;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class FreeApplicationTest extends ApplicationTestCase<Application> {
    Common common;
    TestUtil util;

    public FreeApplicationTest() {
        super(Application.class);
    }
}