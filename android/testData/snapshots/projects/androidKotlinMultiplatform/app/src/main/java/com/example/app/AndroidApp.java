package com.example.app;

import com.example.androidlib.AndroidLib;
import com.example.kmpfirstlib.KmpAndroidFirstLibClass;

public class AndroidApp {

    public String getFromAndroidLib() {
        return new AndroidLib().get();
    }

    public String getFromKmpLib() {
        KmpAndroidFirstLibClass kmp = new KmpAndroidFirstLibClass();
        return kmp.callCommonLibClass() + kmp.callKmpSecondLibClass() + kmp.callAndroidLibClass() +
                kmp.callJvmLibClass();
    }
}
