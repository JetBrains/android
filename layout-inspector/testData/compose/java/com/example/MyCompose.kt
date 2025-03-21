package com.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.core.Modifier
import androidx.compose.ui.core.setContent
import androidx.compose.ui.unit.dp
import com.example.composable.Greeting

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      Column(
        modifier = Modifier.padding(20.dp).clickable(onClick = { selectColumn() }),
      ) {
        Text(text = "Hello")
        Greeting("You")
      }
    }
  }

  private fun selectColumn() {
    // do something...
  }
}
