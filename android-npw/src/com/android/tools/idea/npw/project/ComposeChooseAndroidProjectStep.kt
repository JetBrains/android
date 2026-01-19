/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.copyWithSize

// This should be unified with com.android.studio.ml.bot.ui.compose.timeline.emptystate.Greeting.kt
internal val brandColor1 = Color(0xFF3186FF)
internal val brandColor2 = Color(0xFF346BF1)
internal val brandColor3 = Color(0xFF4FA0FF)
val colors =
  listOf(
    brandColor1,
    brandColor2,
    brandColor3,
    brandColor3,
    brandColor2,
    brandColor1,
    brandColor2,
    brandColor3,
    Color.Transparent,
    Color.Transparent,
  )
val stops = listOf(0f, .09f, .2f, .24f, .35f, .44f, .5f, .56f, .75f, 1f)

@Composable
internal fun GeminiRightPanel(
  textStateFlow: MutableStateFlow<String>,
  geminiPluginAvailable: Boolean,
  hasContextSharing: Boolean,
) {
  Row(modifier = Modifier.fillMaxSize()) {
    Divider(
      orientation = Orientation.Vertical,
      modifier = Modifier.fillMaxHeight(),
      thickness = 1.dp,
    )
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (!geminiPluginAvailable) {
        PermissionsError("Log in to Gemini to create a new project.")
      } else if (!hasContextSharing) {
        PermissionsError("Enable project context sharing to continue.")
      } else {
        NewProjectWizardWithGemini(textStateFlow)
      }
    }
  }
}

@Composable
private fun PermissionsError(text: String) {
  Text(
    modifier = Modifier.padding(bottom = 24.dp),
    text = text,
    style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight(500)),
  )
}

@Composable
private fun NewProjectWizardWithGemini(textStateFlow: MutableStateFlow<String>) {
  val textState = textStateFlow.asStateFlow()
  val textFieldState = remember { TextFieldState(textState.value) }
  // Need to do animation here.
  val brush = CssGradientBrush(angleDegrees = -16.0, colors = colors, stops = stops, scaleX = 4f)

  LaunchedEffect(textFieldState) {
    snapshotFlow { textFieldState.text.toString() }.collectLatest { textStateFlow.value = it }
  }

  Text(
    modifier = Modifier.padding(bottom = 8.dp),
    text = "What do you want to build?",
    style =
      Typography.labelTextStyle()
        .copyWithSize(fontSize = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        .copy(brush = brush),
  )
  Text(
    modifier = Modifier.padding(bottom = 24.dp),
    text = "Bring your app to life faster",
    style =
      TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight(500),
        color = JewelTheme.globalColors.text.info,
      ),
  )
  TextArea(
    modifier = Modifier.size(450.dp, 150.dp),
    state = textFieldState,
    placeholder = { Text(text = "Ask Gemini to create a to-do list app") },
  )
}
