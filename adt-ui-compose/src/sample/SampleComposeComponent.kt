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

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * This sample demonstrates this module's ability to support Compose Desktop development.
 *
 * On running this sample, users should expect an external window to open. Located in the top-left of this
 * window will be a Compose UI button labeled "Hello Compose". This window is defined in the main function
 * and is a JFrame component. It has the SampleComposeComponent JPanel nested in it to host the Compose UI.
 * Thus, this sample also demonstrates the module's ability to support Swing-Compose interoperability.
 *
 * To run this sample in an independent window, run the main function by selecting "Current File" under
 * Intellij's Run Configuration menu and clicking play or debug. Alternatively, you can use the run button
 * adjacent to the main function. Essentially, this sample can be run like any other Java/Kotlin process.
 *
 * It is also possible for users to utilize this sample in their own project. Simply depend on this module
 * and add a new instance of SampleComposeComponent to an existing Swing container.
 */

/**
 * A sample Compose widget (composable) rendering a button labeled "Hello Compose".
 */
@Composable
fun SampleComposable() {
  Button(onClick = {}) {
    Text("Hello Compose")
  }
}

/**
 * A container for the compose UI. Functionally a JPanel that uses the ComposePanel
 * API to host Compose widgets (composables).
 */
class SampleComposeComponent : JPanel(BorderLayout()) {
  init {
    val composePanel = ComposePanel()
    composePanel.setContent {
      SampleComposable()
    }
    add(composePanel, BorderLayout.CENTER)
  }
}

/**
 * Entry point to run the sample. Defines the window of the sample (JFrame), and utilizes
 * the SwingUtilities' invokeAndWait method to create and launch the newly generated window.
 * This window's only content is the SampleComposeComponent JPanel.
 */
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