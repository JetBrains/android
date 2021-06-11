/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui

import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

object ComboCheckBox {

  /**
   * Creates a panel for selecting multiple items
   * @param options The pool of items
   * @param selected The initially selected items
   * @param onOk Action to run with selected items when confirmed
   * @param abbreviate Compact text for item to display within the list
   * @param elaborate Full text for item to display as tooltip
   */
  @JvmStatic @JvmOverloads
  fun <T> of(options: List<T>, selected: Set<T>, onOk: (List<T>) -> Unit,
             okButtonText: String = "Apply",
             abbreviate: (T) -> String = { it.toString() }, elaborate: (T) -> String = { it.toString() }): JPanel {
    val selectionState = selected.toMutableSet()
    val checkBoxes = options.map {
      val title = abbreviate(it)
      JBCheckBox(title).apply {
        isSelected = it in selected
        toolTipText = elaborate(it)
        addItemListener { _ -> if (isSelected) selectionState.add(it) else selectionState.remove(it) }
      }
    }
    val checkBoxList = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      alignmentX = Component.LEFT_ALIGNMENT
      border = JBEmptyBorder(2)
      checkBoxes.forEach { add(it) }
    }
    val scrollPane = JBScrollPane().apply {
      preferredSize = Dimension(checkBoxList.preferredSize.width, 200)
      setViewportView(checkBoxList)
    }
    val header = JBPanel<Nothing>(FlowLayout()).apply {
      add(ActionLink("Select all") { checkBoxes.forEach { it.isSelected = true } })
      add(ActionLink("Deselect all") { checkBoxes.forEach { it.isSelected = false } })
    }
    return JBPanel<Nothing>(BorderLayout()).apply {
      add(header, BorderLayout.NORTH)
      add(scrollPane, BorderLayout.CENTER)
      add(JButton(okButtonText).apply { addActionListener { onOk(selectionState.toList()) } }, BorderLayout.SOUTH)
    }
  }
}