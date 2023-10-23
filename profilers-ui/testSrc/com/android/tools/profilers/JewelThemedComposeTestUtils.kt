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
package com.android.tools.profilers.com.android.tools.profilers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling.Companion.provide
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
fun JewelThemedComposableWrapper(isDark: Boolean, content: @Composable () -> Unit) {
  IntUiTheme(if (isDark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(), provide { arrayOf() }, true) {
    val backgroundColor = if (isDark) JewelTheme.colorPalette.grey(1) else JewelTheme.colorPalette.grey(14)
    Column(Modifier.fillMaxSize().background(backgroundColor)) {
      content()
    }
  }
}