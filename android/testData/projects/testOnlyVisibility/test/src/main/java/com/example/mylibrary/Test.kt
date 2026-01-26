package com.example.mylibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.foo
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Test {
    @Test
    fun testAddition() {
        assertEquals(4, foo(2))
    }
}