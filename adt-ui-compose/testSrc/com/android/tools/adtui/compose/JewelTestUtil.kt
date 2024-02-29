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

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling

@Composable
fun JewelTestTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
  val themeDefinition =
    if (darkMode) {
      JewelTheme.darkThemeDefinition()
    } else {
      JewelTheme.lightThemeDefinition()
    }

  val componentStyling =
    if (darkMode) {
      ComponentStyling.dark()
    } else {
      ComponentStyling.light()
    }

  IntUiTheme(themeDefinition, componentStyling, true) { content() }
}