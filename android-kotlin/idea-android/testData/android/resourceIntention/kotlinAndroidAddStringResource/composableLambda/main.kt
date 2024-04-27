package com.myapp

import androidx.compose.runtime.Composable

@Composable fun myComposeWithLambda(block: @Composable () -> Unit) {}

@Composable
fun myCompose() {
  myComposeWithLambda {
    val str = "some <caret>text with newline \n and trailing space "
  }
}
