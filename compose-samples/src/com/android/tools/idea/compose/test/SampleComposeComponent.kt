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
package com.android.tools.idea.compose.test

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

@Composable
fun SampleComposeWidget() {
  Column {
    Button(onClick = {}) {
      Text("Hello Compose")
    }
  }
}

class SampleComposeComponent: JPanel(BorderLayout()) {
  init {
    val composePanel = ComposePanel()
    composePanel.setContent {
      SampleComposeWidget()
    }
    add(composePanel, BorderLayout.CENTER)
  }
}

fun main() {
  SwingUtilities.invokeAndWait {
    val window = JFrame()
    val panel = SampleComposeComponent()
    with(window) {
      defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
      title = "Compose Test Window"
      contentPane.add(panel, BorderLayout.CENTER)
      setSize(800, 600)
      isVisible = true
    }
  }
}