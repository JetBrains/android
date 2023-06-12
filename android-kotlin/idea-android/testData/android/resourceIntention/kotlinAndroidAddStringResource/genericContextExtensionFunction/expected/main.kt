package com.myapp

import android.content.Context

fun <T: Context> T.getText(): String? = getString(R.string.some_text)
