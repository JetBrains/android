/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.CheckboxRow
import org.jetbrains.jewel.Divider
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import sample.components.Buttons
import sample.components.Checkboxes
import sample.components.Dropdowns

/**
 * This sample demonstrates this module's ability to support Jewel + Compose Desktop development.
 *
 * On running this sample, users should expect an external window to open. Within this sample you will
 * find example components showcasing the Jewel component library. At the top of this window, one will
 * find two checkboxes allowing the user to switch the top-level theme and toggle the Swing interoperability.
 * One toggle of these settings, the sample components will adapt accordingly, showcasing the functionality
 * Jewel brings on top of the foundation compose desktop components.
 *
 * To run this sample in an independent window, run the main function by selecting "Current File" under
 * Intellij's Run Configuration menu and clicking play or debug. Alternatively, you can use the run button
 * adjacent to the main function. Essentially, this sample can be run like any other Java/Kotlin process.
 *
 * This sample window showcasing different compose components was adapted from the public Jewel repository
 * standalone sample. Additionally, an example of an Intellij IDE plugin using Jewel is available in the
 * repository is. See: https://github.com/JetBrains/jewel.
 */

/**
 * Entry point to run the sample. Defines the window of the sample, the top-level theming resources, and a
 * component unifying all sample components into one "Component Showcase".
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  singleWindowApplication(
    title = "Jewel component catalog",
  ) {
    var isDark by remember { mutableStateOf(false) }
    var swingCompat by remember { mutableStateOf(false) }
    val theme = if (isDark) IntUiTheme.darkThemeDefinition() else IntUiTheme.lightThemeDefinition()

    IntUiTheme(theme, swingCompat) {
      val resourceLoader = LocalResourceLoader.current

      val windowBackground = if (isDark) {
        IntUiTheme.colorPalette.grey(1)
      }
      else {
        IntUiTheme.colorPalette.grey(14)
      }

      Column(Modifier.fillMaxSize().background(windowBackground)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CheckboxRow("Dark", isDark, resourceLoader, { isDark = it })
          CheckboxRow("Swing compat", swingCompat, resourceLoader, { swingCompat = it })
        }
        Divider(Modifier.fillMaxWidth())
        ComponentShowcase()
      }
    }
  }
}

@Composable
private fun ComponentShowcase() {
  val verticalScrollState = rememberScrollState()

  Box(Modifier.fillMaxSize()) {
    Column(
      Modifier.width(IntrinsicSize.Max)
        .verticalScroll(verticalScrollState)
        .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      Buttons()
      Dropdowns()
      Checkboxes()
    }
  }
}