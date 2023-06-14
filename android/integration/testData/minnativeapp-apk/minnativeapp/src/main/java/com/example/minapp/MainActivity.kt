package com.example.minnativeapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this)
        label.setText("Hello Minimal World!")
        Log.i("MainActivity", "Hello Minimal World!")
        setContentView(label)
        Log.i("MainActivity", helloFromJni())
    }

    private external fun helloFromJni() : String

    companion object {
        init {
            System.loadLibrary("minnativeapp")
        }
    }
}
