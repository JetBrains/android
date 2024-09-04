/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.theme.colorPalette

/** Demonstration of the device screen diagram in a standalone app. */
fun main() {
  singleWindowApplication(title = "Screen Diagram") {
    val isDark = true
    IntUiTheme(isDark = isDark) {
      val windowBackground =
        if (isDark) {
          JewelTheme.colorPalette.gray(1)
        } else {
          JewelTheme.colorPalette.gray(14)
        }
      Box(Modifier.background(windowBackground)) {
        var round by remember { mutableStateOf(false) }
        DeviceScreenDiagram(
          800,
          if (round) 800 else 1200,
          diagonalLength = "6.2\"",
          round = round,
          modifier = Modifier.clickable { round = !round },
        )
      }
    }
  }
}