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
        Log.w("MainActivity", "Hello Minimal World!")
        setContentView(label)
        Log.w("MainActivity", helloFromJni())
        Log.w("MainActivty", "After breakpoint")
    }

    private external fun helloFromJni() : String

    companion object {
        init {
            System.loadLibrary("minnativeapp")
        }
    }
}
