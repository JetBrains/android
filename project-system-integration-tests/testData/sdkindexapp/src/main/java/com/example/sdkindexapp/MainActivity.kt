package com.example.sdkindexapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this)
        label.setText("Hello Sdk Index World!")
        Log.i("MainActivity", "Hello Sdk Index World!")
        setContentView(label)
    }
}