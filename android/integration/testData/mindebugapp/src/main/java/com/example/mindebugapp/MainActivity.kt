package com.example.mindebugapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this)
        label.setText("Hello Minimal Debug World!")
        Log.i("MainActivity", "Hello Minimal Debug World!")
        setContentView(label)
        Thread {
          while (true) {
            try {
              Thread.sleep(100)
            } catch (e: InterruptedException) {
              // do nothing
            }
          }
        }.start()
        Log.i("MainActivity", "Goodbye Minimal Debug World!")
    }
}
