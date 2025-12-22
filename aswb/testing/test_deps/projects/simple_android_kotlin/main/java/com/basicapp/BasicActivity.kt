package com.basicapp

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.TextView

/**
 * The main activity of the Basic Sample App.
 */
class BasicActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.basic_activity)

    val buttons = listOf<Button>(
      findViewById(R.id.button_id_fizz),
      findViewById(R.id.button_id_buzz)
    )

    for (b in buttons) {
      b.setOnClickListener { v ->
        val tv = findViewById<TextView>(R.id.text_hello)
        if (v.id == R.id.button_id_fizz) {
          tv.text = getString(R.string.generated_string)
        } else if (v.id == R.id.button_id_buzz) {
          tv.text = "buzz"
        }
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    // Inflate the menu; this adds items to the action bar if it is present.
    menuInflater.inflate(R.menu.menu, menu)
    return true
  }
}
