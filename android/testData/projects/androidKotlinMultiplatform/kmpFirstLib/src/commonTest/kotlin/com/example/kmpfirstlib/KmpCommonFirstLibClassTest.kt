package com.example.kmpfirstlib

import org.junit.Test

class KmpCommonFirstLibClassTest {

    @Test
    fun testThatPasses() {
        val x = KmpCommonFirstLibClass()
        assert(x.get() == "I'm here")
    }
}
