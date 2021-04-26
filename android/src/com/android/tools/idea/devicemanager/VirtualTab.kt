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
package com.android.tools.idea.devicemanager

import com.android.tools.idea.devicemanager.displayList.VirtualDisplayList
import com.android.tools.idea.devicemanager.displayList.PreconfiguredDisplayList
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.BadLocationException
import javax.swing.text.Document

class VirtualTab(project: Project, toolWindow: ToolWindow) {
  val avdDisplayList = VirtualDisplayList(project)
  val preconfiguredDisplayList = PreconfiguredDisplayList(project, avdList = avdDisplayList)

  private val searchDocumentListener = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) {
      avdDisplayList.updateSearchResults(getText(e.document))
    }

    override fun removeUpdate(e: DocumentEvent) {
      avdDisplayList.updateSearchResults(getText(e.document))
    }

    override fun changedUpdate(e: DocumentEvent) {
      avdDisplayList.updateSearchResults(getText(e.document))
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

  val toolbar = VirtualToolbar(avdDisplayList, avdDisplayList, searchDocumentListener)

  val content = panel(LCFlags.fill) {
    row {
      toolbar.panel(growX)
    }
    row {
      avdDisplayList(grow, push)
    }
    row {
      label("Recommended configurations")
    }
    row {
      preconfiguredDisplayList(grow, push)
    }
  }
}
