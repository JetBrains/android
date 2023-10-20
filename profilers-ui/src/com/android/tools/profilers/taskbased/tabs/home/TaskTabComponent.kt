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
package com.android.tools.profilers.taskbased.tabs.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import com.intellij.ui.JBColor
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.bridge.SwingBridgeTheme
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import java.awt.BorderLayout
import javax.swing.JPanel

@OptIn(ExperimentalJewelApi::class)
abstract class TaskTabComponent(tabContent: @Composable () -> Unit): JPanel(BorderLayout()) {
  init {
    enableNewSwingCompositing()
    val composePanel = ComposePanel()
    composePanel.setContent {
      SwingBridgeTheme {
        val bgColor by remember(IntUiTheme.isDark) { mutableStateOf(JBColor.PanelBackground.toComposeColor()) }
        Row(
          modifier = Modifier.fillMaxSize().background(bgColor),
        ) {
          tabContent()
        }
      }
    }
    add(composePanel, BorderLayout.CENTER)
  }
}
