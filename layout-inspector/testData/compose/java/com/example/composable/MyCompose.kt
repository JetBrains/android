package com.example.composable

import androidx.compose.foundation.Text
import androidx.compose.runtime.Composable

@Composable
fun Greeting(name: String) {
  Text(text = "Hello $name!")
}
