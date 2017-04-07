package com.example;

import android.app.Application;
import android.test.ApplicationTestCase;

import static org.junit.Assert.*; // unresolved (unit test's library dependency)


/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
    Common common;
    TestUtil util;

    ExampleUnitTest unitTest; // unresolved (defined in unit test)
    Lib lib;  // unresolved (unit test's module dependency)

    public ApplicationTest() {
        super(Application.class);
    }
}