package com.myapp

import androidx.compose.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun myCompose() {
  val str = stringResource(R.string.some_text_with_newline_and_trailing_space)
}