package com.example.applychanges

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
    }

    override fun onResume() {
      super.onResume()
      // EASILY SEARCHABLE ONRESUME LINE (this comment exists for the test to be able to find/replace from here until the next comment)
      printBefore()
      // END ONRESUME SEARCH
    }

    fun printBefore() {
      Log.i("MainActivity", "OnResume Before with resource status: " + getString(R.string.status))
    }

    fun printAfter() {
      Log.i("MainActivity", "OnResume After with resource status: " + getString(R.string.status))
    }
}
