package com.example.kmpfirstlib;

import com.example.androidlib.AndroidLib;
import com.example.kmpjvmonly.KmpJvmOnlyLibClass;
import com.example.kmpsecondlib.KmpAndroidSecondLibClass;

public class KmpAndroidFirstLibJavaClass {

    public String callCommonLibClass() {
        return new KmpCommonFirstLibClass().get();
    }

    public String callKmpSecondLibClass() {
        return new KmpAndroidSecondLibClass().callCommonLibClass();
    }

    public String callAndroidLibClass() {
        return new AndroidLib().get();
    }

    public String callJvmLibClass() {
        return new KmpJvmOnlyLibClass().callCommonLibClass();
    }

    public String callKotlinClass() {
        return new KmpAndroidFirstLibClass().callCommonLibClass();
    }
}
