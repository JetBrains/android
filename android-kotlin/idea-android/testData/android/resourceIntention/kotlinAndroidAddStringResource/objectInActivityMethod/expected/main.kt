package com.myapp

import android.app.Activity

class MyActivity: Activity() {
    fun foo() {
        object {
            val text = getString(R.string.some_string)
        }
    }
}