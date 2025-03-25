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
import javax.swing.JComponent
import org.jetbrains.jewel.bridge.JewelComposeNoThemePanel
import org.jetbrains.jewel.bridge.JewelToolWindowNoThemeComposePanel
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

@Suppress("FunctionName")
@OptIn(ExperimentalJewelApi::class)
fun StudioComposePanel(content: @Composable () -> Unit): JComponent = JewelComposeNoThemePanel {
  StudioTheme(content)
}

@Suppress("FunctionName")
@OptIn(ExperimentalJewelApi::class)
fun StudioToolWindowComposePanel(content: @Composable () -> Unit): JComponent =
  JewelToolWindowNoThemeComposePanel {
    StudioTheme(content)
  }