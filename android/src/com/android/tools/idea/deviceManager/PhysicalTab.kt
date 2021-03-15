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
package com.android.tools.idea.deviceManager

import com.android.tools.idea.deviceManager.physicaltab.PhysicalDevicePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.layout.panel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.BadLocationException
import javax.swing.text.Document

class PhysicalTab(project: Project, toolWindow: ToolWindow) {
  private val searchDocumentListener = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) {
      // TODO
    }

    override fun removeUpdate(e: DocumentEvent) {
      // TODO
    }

    override fun changedUpdate(e: DocumentEvent) {
      // TODO
    }

    private fun getText(d: Document): String {
      return try {
        d.getText(0, d.length)
      }
      catch (e: BadLocationException) {
        ""
      }
    }
  }

  val avdDisplayList = PhysicalDevicePanel(project)
  val toolbar = PhysicalToolbar(project, searchDocumentListener)
  val content = panel {
    row  {
      toolbar.panel(growX)
    }
    row {
      avdDisplayList(grow, push)
    }
  }
}
