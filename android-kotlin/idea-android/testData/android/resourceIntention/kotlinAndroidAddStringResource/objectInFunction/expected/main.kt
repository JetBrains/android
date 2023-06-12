package com.myapp

import android.app.Activity
import android.content.Context

fun foo(context: Context) {
    object {
        val text = context.getString(R.string.some_string)
    }
}