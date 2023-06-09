package com.example.kmpfirstlib

import com.example.androidlib.AndroidLib
import com.example.kmpjvmonly.KmpJvmOnlyLibClass
import com.example.kmpsecondlib.KmpAndroidSecondLibClass
import java.time.LocalDate

class KmpAndroidFirstLibClass {

    fun callCommonLibClass(): String {
        return KmpCommonFirstLibClass().get()
    }

    fun callKmpSecondLibClass(): String {
        return KmpAndroidSecondLibClass().callCommonLibClass()
    }

    fun callAndroidLibClass(): String {
        return AndroidLib().get()
    }

    fun callJvmLibClass(): String {
        return KmpJvmOnlyLibClass().callCommonLibClass()
    }
}
