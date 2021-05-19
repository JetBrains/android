package com.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.*
import androidx.ui.core.Modifier
import androidx.ui.core.setContent
import androidx.ui.unit.dp
import com.example.composable.Greeting

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      Column(modifier = Modifier.padding(20.dp)) {
        Text(text = "Hello")
        Greeting("You")
      }
    }
  }
}
