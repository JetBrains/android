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
package com.android.tools.idea.ui.resourcemanager.widget

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.util.Vector
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class DetailedPreview : JPanel(null) {

  private val label = JBLabel(null, JBLabel.CENTER)
  private val tableModel = object : DefaultTableModel(0, 2) {
    override fun isCellEditable(row: Int, column: Int) = false
  }

  var data: Map<String, String> = emptyMap()
    set(value) {
      field = value
      tableModel.rowCount = value.size
      tableModel.setDataVector(
        value
          .map { (key, value) ->
            Vector<String>(2).apply {
              addElement("$key:")
              addElement(value)
            }
          }
          .toCollection(Vector<Vector<String>>(value.size)), Vector<String>(listOf("", "")))
      metadataTable.revalidate()
      metadataTable.repaint()
    }

  var icon: Icon? = null
    set(value) {
      field = value
      label.icon = value
    }

  private val metadataTable = JBTable(tableModel).apply {
    alignmentX = LEFT_ALIGNMENT
    rowHeight = JBUI.scale(28)
    rowMargin = JBUI.scale(8)
    background = UIUtil.getPanelBackground()
    setShowGrid(false)
  }

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)

    add(JBLabel("Preview").apply {
      horizontalAlignment = JBLabel.LEFT
      alignmentX = LEFT_ALIGNMENT
      border = JBUI.Borders.empty(8, 0)
    })

    add(ChessBoardPanel(BorderLayout()).apply {
      alignmentX = LEFT_ALIGNMENT
      border = JBUI.Borders.customLine(JBColor.border(), 1)
      showChessboard = true
      preferredSize = JBUI.size(300, 300)
      minimumSize = JBUI.size(0, 200)
      maximumSize = JBUI.size(2000, 200)
      add(label)
    })

    add(metadataTable)
  }

}