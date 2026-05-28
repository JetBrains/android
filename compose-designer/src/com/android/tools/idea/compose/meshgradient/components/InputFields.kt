/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.meshgradient.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.tools.idea.compose.meshgradient.formatFloat
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun ParameterSwatch(text: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
    Text(text, color = JewelTheme.globalColors.text.info)
  }
}

@Composable
fun DimensionInputField(
  value: Int,
  modifier: Modifier = Modifier,
  min: Int? = null,
  max: Int? = null,
  enabled: Boolean = true,
  paramName: String,
  onUpdate: (Int) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val textFieldState = remember(value) { TextFieldState(value.toString()) }

  LaunchedEffect(Unit) {
    snapshotFlow { textFieldState.text }
      .collectLatest {
        val filteredValue = it.filter { char -> char.isDigit() }
        textFieldState.edit { replace(0, textFieldState.text.length, filteredValue) }
      }
  }

  fun reset() {
    textFieldState.edit { replace(0, textFieldState.text.length, value.toString()) }
  }

  fun validate() {
    try {
      textFieldState.text.toString().toIntOrNull()?.let { next ->
        if (next != value) {
          val nextValue =
            next.let {
              if (min != null && max != null) {
                it.coerceIn(min, max)
              } else if (min != null) {
                it.coerceAtLeast(min)
              } else if (max != null) {
                it.coerceAtMost(max)
              } else {
                it
              }
            }

          onUpdate(nextValue)
          textFieldState.edit { replace(0, textFieldState.text.length, nextValue.toString()) }
        }
      } ?: run { reset() }
    } catch (e: Exception) {
      println(e.message)
    }
  }

  TextField(
    state = textFieldState,
    enabled = enabled,
    leadingIcon = { ParameterSwatch(text = paramName, modifier = Modifier.height(16.dp).padding(end = 6.dp)) },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    onKeyboardAction = {
      validate()
      focusManager.clearFocus()
    },
    modifier =
      modifier
        .onFocusChanged { validate() }
        .onKeyEvent {
          when (it.key) {
            Key.Tab -> {
              validate()
              return@onKeyEvent false
            }

            Key.Escape -> {
              reset()
              focusManager.clearFocus()
            }
          }
          return@onKeyEvent true
        },
  )
}

@Composable
fun OffsetInputField(value: Float, modifier: Modifier = Modifier, enabled: Boolean = true, paramName: String, onUpdate: (Float) -> Unit) {
  val focusManager = LocalFocusManager.current
  val textFieldState = remember(value) { TextFieldState(formatFloat(value)) }

  fun reset() {
    textFieldState.edit { replace(0, textFieldState.text.length, formatFloat(value)) }
  }

  fun validate() {
    textFieldState.text.toString().toFloatOrNull()?.let { next ->
      if (next != value) {
        onUpdate(next.coerceIn(0f, 1f))
      }
    } ?: run { reset() }
  }

  TextField(
    state = textFieldState,
    enabled = enabled,
    leadingIcon = { ParameterSwatch(text = paramName, modifier = Modifier.size(16.dp).padding(end = 6.dp)) },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    onKeyboardAction = {
      validate()
      focusManager.clearFocus()
    },
    modifier =
      modifier
        .onFocusChanged { validate() }
        .onKeyEvent {
          when (it.key) {
            Key.Tab -> {
              validate()
              return@onKeyEvent false
            }

            Key.Escape -> {
              reset()
              focusManager.clearFocus()
            }
          }
          return@onKeyEvent true
        },
  )
}
