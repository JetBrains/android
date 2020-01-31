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
package com.android.tools.adtui.validation

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Dialog for displaying multi-line error messages.
 *
 * @param titleText the title of the dialog
 * @param headerLabel the text of the header label
 * @param errorText the text shown in the main text area, or null to leave blank
 */
class ErrorDetailDialog(titleText: String,
                        private val headerLabel: String,
                        errorText: String?
) : DialogWrapper(false) {
  private val textArea = JTextArea(30, 130)
  private var header = JLabel()

  init {
    textArea.text = errorText
    textArea.isEditable = false
    textArea.caretPosition = 0
    init()
    title = titleText
    isModal = false
  }

  /**
   * Sets text of the header label.
   */
  fun setHeaderLabel(text: String) {
    header.text = text
  }

  /**
   * Sets contents of the main text area, or clears the area if null.
   */
  fun setText(errorText: String?) {
    textArea.text = errorText
  }

  override fun createCenterPanel(): JComponent? {
    val pane = JPanel(BorderLayout(0, JBUI.scale(5)))

    header.text = headerLabel

    pane.add(header, BorderLayout.PAGE_START)
    pane.add(JBScrollPane(textArea), BorderLayout.CENTER)

    return pane
  }

  override fun createActions(): Array<Action> = arrayOf(cancelAction)

  override fun createDefaultActions() {
    super.createDefaultActions()
    cancelAction.putValue(Action.NAME, "Close")
  }
}
