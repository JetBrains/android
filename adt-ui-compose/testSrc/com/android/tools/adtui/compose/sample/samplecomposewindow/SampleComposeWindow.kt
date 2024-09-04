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
package com.android.tools.adtui.compose.sample.samplecomposewindow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.theme.colorPalette
import sample.samplecomposewindow.ComponentShowcase

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
 *
 * This sample application utilizes the Jewel standalone theme which is for scoped for testing only.
 * NOTE: The Jewel standalone theme should only ever be used for testing and never used in production code.
 */
fun main() {
  singleWindowApplication(
    title = "Jewel component catalog",
  ) {
    var swingCompat by remember { mutableStateOf(false) }
    var isDark by remember { mutableStateOf(false) }

    val themeDefinition =
      if (isDark) {
        JewelTheme.darkThemeDefinition()
      } else {
        JewelTheme.lightThemeDefinition()
      }

    IntUiTheme(themeDefinition, ComponentStyling.provide { arrayOf() }, swingCompat) {
      val windowBackground = if (isDark) {
        JewelTheme.colorPalette.gray(1)
      } else {
        JewelTheme.colorPalette.gray(14)
      }

      Column(Modifier.fillMaxSize().background(windowBackground)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CheckboxRow("Dark", isDark, { isDark = !isDark })
          CheckboxRow("Swing compat", swingCompat, { swingCompat = it })
        }
        ComponentShowcase()
      }
    }
  }
}