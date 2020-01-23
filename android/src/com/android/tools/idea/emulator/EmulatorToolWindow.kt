/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.awt.BorderLayout
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class EmulatorToolWindow(project: Project) : DumbAware {
  private val toolWindowContent: JPanel
  private var initialized = false

  val component: JComponent
    get() = toolWindowContent

  private fun createContent(window: ToolWindow) {
    initialized = true
    try { // TODO: Well we should probably fetch the proper emulator port from somewhere.
      val emulatorPanel = EmulatorJarLoader.createView(5554)
      // Tor modifications: wrap in another JPanel to keep aspect ratio
      val layoutManager: LayoutManager = EmulatorLayoutManager(emulatorPanel)
      toolWindowContent.add(emulatorPanel)
      toolWindowContent.layout = layoutManager
      toolWindowContent.repaint()
      window.title = EmulatorJarLoader.getCurrentAvdName(emulatorPanel)
    }
    catch (e: Exception) {
      val label = "Unable to load emulator view: $e"
      toolWindowContent.add(JLabel(label), BorderLayout.CENTER)
    }
  }

  private fun destroyContent() {
    initialized = false
    toolWindowContent.layout = BorderLayout()
    toolWindowContent.removeAll()
  }

  init {
    toolWindowContent = JPanel(BorderLayout())

    // Lazily initialize content since we can only have one frame.
    project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged() {
        if (!SHUTDOWN_CAPABLE && initialized) {
          return
        }
        // We need to query the tool window again, because it might have been unregistered when closing the project.
        val window = ToolWindowManager.getInstance(project).getToolWindow(
          ID) ?: return
        if (window.isVisible) { // TODO: How do I unsubscribe? This will keep notifying me of all tool windows, forever.
          if (initialized) {
            return
          }
          initialized = true
          createContent(window)
        }
        else if (SHUTDOWN_CAPABLE && initialized) {
          destroyContent()
        }
      }
    })
  }

  companion object {
    const val SHUTDOWN_CAPABLE = false
    const val ID = "Emulator"
  }
}