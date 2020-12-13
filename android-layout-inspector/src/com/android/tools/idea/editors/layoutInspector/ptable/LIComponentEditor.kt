/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector.ptable

import com.android.tools.property.ptable.PTableItem
import com.intellij.openapi.util.SystemInfo
import javax.swing.JComponent
import java.awt.event.ActionEvent
import javax.swing.JPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ActionListener

/**
 * Used by [LITableCellEditor] to provide the JComponent for editing a cell.
 */
class LIComponentEditor {
  private val myTextField: JBTextField = JBTextField()
  private val myPanel: JPanel

  var property: PTableItem? = null
    set(property) {
      field = property
      val propValue = this.property!!.value
      myTextField.text = propValue
    }

  val component: JComponent
    get() = myPanel

  val value: Any?
    get() = property?.value

  init {
    myTextField.addActionListener(ActionListener { this.textChanged(it) })
    val fg = UIUtil.getTableSelectionForeground(true)
    val bg = UIUtil.getTableSelectionBackground(true)
    myPanel = JPanel(BorderLayout(if (SystemInfo.isMac) 0 else 2, 0))
    myPanel.foreground = fg
    myPanel.background = bg
    myPanel.add(myTextField)
  }

  private fun textChanged(event: ActionEvent) {
    property!!.setValue(myTextField.text)
  }
}
