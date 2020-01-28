/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.intellij.execution.runners.ExecutionUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)

/**
 * Represents contents of the Emulator tool window for a single Emulator instance.
 */
class EmulatorToolWindowPane(val title: String, val port: Int) {
  private val panel: JPanel = JPanel(BorderLayout())

  val id = port.toString()

  val icon
    get() = ICON

  val component: JComponent
    get() = panel

  fun createContent() {
    try {
      // TODO: Switch to using gRPC Emulator API.
      val emulatorPanel = EmulatorJarLoader.createView(port)
      // Wrap emulatorPanel in another JPanel to keep aspect ratio.
      val layoutManager: LayoutManager = EmulatorLayoutManager(emulatorPanel)
      panel.add(emulatorPanel)
      panel.layout = layoutManager
      panel.repaint()
    }
    catch (e: Exception) {
      val label = "Unable to load emulator view: $e"
      panel.add(JLabel(label), BorderLayout.CENTER)
    }
  }

  fun destroyContent() {
    panel.layout = BorderLayout()
    panel.removeAll()
  }
}