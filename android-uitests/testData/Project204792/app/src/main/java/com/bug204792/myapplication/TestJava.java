package com.bug204792.myapplication;

import android.util.Log;

public class TestJava {

    public String mPublicInstance = "New ";

    private String mPrivateInstance = " private instance";

    public static final String TAG = TestJava.class.getSimpleName();
    public void publicInstanceMethod() {
        int x=100;
        int y =200;
        int z= 300;
    }

    public String getPublicInstVar(String add){
        return mPublicInstance + add + mPrivateInstance;
    }
}
