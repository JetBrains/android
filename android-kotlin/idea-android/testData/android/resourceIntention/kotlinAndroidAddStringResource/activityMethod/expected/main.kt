package com.myapp

import android.app.Activity

class MyActivity: Activity() {
    fun foo() {
        val a = getString(R.string.some_string)
    }
}