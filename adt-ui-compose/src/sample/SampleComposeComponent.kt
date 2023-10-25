/*
 * Copyright (C) 2023 The Android Open Source Project
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
package sample

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

/**
 * This composable is used by the SampleComposeComponentTest to show how we can test the content and behavior of compose components.
 * For theming and swing compatibility, we hardcode the values for simplicity.
 */
@Composable
fun SampleComposeComponent() {
  var displayText by remember { mutableStateOf(false) }
  Column {
    DefaultButton(onClick = { displayText = !displayText }) {
      Text("Hello Compose")
    }
    if (displayText) {
      Text("Displayed Text", color = Color.Red)
    }
  }
}