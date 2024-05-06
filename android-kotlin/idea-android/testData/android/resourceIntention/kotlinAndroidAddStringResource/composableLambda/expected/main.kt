package com.myapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable fun myComposeWithLambda(block: @Composable () -> Unit) {}

@Composable
fun myCompose() {
  myComposeWithLambda {
    val str = stringResource(R.string.some_text_with_newline_and_trailing_space)
  }
}
