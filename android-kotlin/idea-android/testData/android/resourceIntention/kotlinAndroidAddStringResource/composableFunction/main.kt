package com.myapp

import androidx.compose.runtime.Composable

@Composable
fun myCompose() {
  val str = "some <caret>text with newline \n and trailing space "
}