package com.google.firebase.assistant.test

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  fun onCreate() {
    Log.i("Hello", "world")
    Toast.makeText(this, "Some code", Toast.LENGTH_SHORT).show()
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Toast.makeText(this, "Success", Toast.LENGTH_LONG).show()

    if (true) {
      throw IllegalArgumentException("Hello")
    }

    findViewById<View>(R.id.button).setOnClickListener { RuntimeInit.foo() }
  }

  fun randomMethod() {
    findViewById<View>(R.id.button).setOnClickListener {
      RuntimeInit.consume { string ->
        println(string)
        RuntimeInit.foo()
      }
    }
  }
}
