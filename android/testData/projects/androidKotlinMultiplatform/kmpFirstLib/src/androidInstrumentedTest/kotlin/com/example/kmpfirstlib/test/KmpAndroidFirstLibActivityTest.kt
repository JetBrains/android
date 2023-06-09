package com.example.kmpfirstlib.test

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.example.kmpfirstlib.KmpAndroidActivity
import com.example.kmpfirstlib.KmpAndroidFirstLibClass

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KmpAndroidFirstLibActivityTest {

    @Test
    fun testActivityThatPasses() {
        val scenario = launch(KmpAndroidActivity::class.java)
        scenario.onActivity { activity ->
          val x = KmpAndroidFirstLibClass()
          Assert.assertTrue(x.callCommonLibClass() == x.callAndroidLibClass())
          Assert.assertTrue(x.callKmpSecondLibClass() == x.callAndroidLibClass())
          Assert.assertTrue(x.callJvmLibClass() == x.callAndroidLibClass())
        }
    }

    @Test
    fun testJavaResources() {
        val kmpResValue = this.javaClass.classLoader.getResourceAsStream("kmp_resource.txt").use {
            it!!.bufferedReader().readLine()
        }

        Assert.assertTrue(kmpResValue == "kmp resource")

        val androidLibResValue = this.javaClass.classLoader.getResourceAsStream("android_lib_resource.txt").use {
            it!!.bufferedReader().readLine()
        }

        Assert.assertTrue(androidLibResValue == "android lib resource")
    }
}
