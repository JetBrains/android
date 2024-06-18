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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.awt.ComposePanel
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import javax.swing.JComponent

@OptIn(ExperimentalJewelApi::class)
fun StudioComposePanel(
  content: @Composable () -> Unit,
): JComponent =
  ComposePanel().apply {
    setContent {
      StudioTheme {
        CompositionLocalProvider(LocalComponent provides this@apply) {
          ComponentDataProviderBridge(this@apply, content = content)
        }
      }
    }
  }