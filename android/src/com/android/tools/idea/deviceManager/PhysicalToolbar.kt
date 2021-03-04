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

import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.deviceManager.displayList.openWifiPairingDialog
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import javax.swing.JButton
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A toolbar which contains a [panel] which is an UI element.
 */
class PhysicalToolbar(
  val project: Project?,
  searchDocumentListener: DocumentListener
) {
  private val newButton = JButton("Pair using Wi-Fi").apply {
    addActionListener { if (project != null) openWifiPairingDialog(project) }
  }

  private val refreshButton = CommonButton(AllIcons.Actions.Refresh).apply {
    // TODO(qumeric): do we need it? (supposed to run `adb` reconnect)
  }

  // TODO(qumeric): set correct link
  private val helpButton = CommonButton(AllIcons.Actions.Help).apply {
    addActionListener { BrowserUtil.browse("http://developer.android.com/r/studio-ui/virtualdeviceconfig.html") }
  }

  private val separator = Separator()

  private val searchField = createSearchField("Search physical devices by name").apply {
    addDocumentListener(searchDocumentListener)
  }

  // TODO(qumeric): probably we should implement some interface with CommonToolbar instead of having a public field.
  // Consider using delegation? Or some pattern like MVVM.
  val panel = panel {
    row {
      newButton().withLeftGap()
      separator()
      refreshButton()
      helpButton()
      searchField(growX, pushX)
    }
  }
}

// From the resource explorer toolbar. Move to adtui?
private fun createSearchField(name: String, gap: Int = JBUI.scale(10)) = SearchTextField(true).apply {
  isFocusable = true
  toolTipText = name
  accessibleContext.accessibleName = name
  textEditor.columns = gap
  textEditor.document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      // TODO(qumeric)
    }
  })
}
