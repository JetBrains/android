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
package com.android.tools.studio.labs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.flags.Flag
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.painterResource
import org.jetbrains.jewel.ui.theme.colorPalette
import com.intellij.openapi.application.invokeLater


/** Class representing a Studio Labs Feature Panel. */
class StudioLabsFeaturePanelUi(
  val flag: Flag<Boolean>,
  val heading: String,
  val description: String,
  val imageSourceDefault: String,
  val imageSourceDark: String,
  val imageDescription: String,
) {
  private val currentState = mutableStateOf(flag.get())

  @Composable
  fun PanelContent() {
    Column(
      modifier =
        Modifier.width(300.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(
            if (JewelTheme.isDark) {
              JewelTheme.colorPalette.gray(3)
            } else {
              JewelTheme.colorPalette.gray(12)
            }
          )
    ) {
      Image(
        painter =
          if (JewelTheme.isDark) {
            painterResource(imageSourceDark)
          } else {
            painterResource(imageSourceDefault)
          },
        imageDescription,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentScale = ContentScale.FillWidth,
      )
      Column(modifier = Modifier.padding(16.dp)) {
        Text(heading, style = Typography.h2TextStyle(), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.size(12.dp))
        Text(description)
        Spacer(modifier = Modifier.size(16.dp))
        OutlinedButton(
          onClick = { currentState.value = !currentState.value },
          modifier = Modifier.testTag("enable_disable_button"),
        ) {
          Text(if (currentState.value) "Disable" else "Enable")
        }
      }
    }
  }

  fun isModified(): Boolean {
    return flag.get() != currentState.value
  }

  fun apply() {
    val newValue  = currentState.value
    return invokeLater {
      flag.override(newValue)
    }
  }

  fun reset() {
    currentState.value = flag.get()
  }
}
