package com.example.mylibrary;

/**
 * Created by nishanthkumarg on 11/4/16.
 */

public class ExternalClass {

    LibClass lc = new LibClass();

    public ExternalClass() {
        lc.sampleMethod();
    }
}
