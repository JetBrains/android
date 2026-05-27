package org.example.project

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.movableContentOf
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember

@Composable
fun ValidRememberUsageTest() {
  val req = remember { FocusRequester() }
  val mov = remember { movableContentOf { } }
  val color = remember { Animatable(0f) }
}