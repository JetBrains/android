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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.android.tools.adtui.HoverRowTable
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

/**
 * View class for the WorkManger Inspector Tab with a table of all active works.
 */
class WorkManagerInspectorTab(private val client: WorkManagerInspectorClient) {

  private class WorksTableCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component =
      when (table?.convertColumnIndexToModel(column)) {
        // TODO(163343710): Add icons on the left of state text
        WorksTableModel.Column.STATE.ordinal -> {
          val text = WorkManagerInspectorProtocol.WorkInfo.State.forNumber(value as Int).name
          val capitalizedText = text[0] + text.substring(1).toLowerCase(Locale.getDefault())
          super.getTableCellRendererComponent(table, capitalizedText, isSelected, hasFocus, row, column)
        }
        WorksTableModel.Column.TIME_STARTED.ordinal -> {
          val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
          val time = value as Long
          val timeText = if (time == -1L) "-" else formatter.format(Date(value))
          super.getTableCellRendererComponent(table, timeText, isSelected, hasFocus, row, column)
        }
        else -> super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      }
  }

  private val root = JPanel(VerticalLayout(0))

  val component: JComponent = root

  init {
    root.add(JScrollPane(buildWorksTable()))
  }

  private fun buildWorksTable(): JBTable {
    val model = WorksTableModel(client)
    val table: JBTable = HoverRowTable(model)

    table.autoCreateRowSorter = true
    table.setDefaultRenderer(Object::class.java, WorksTableCellRenderer())

    // Adjusts width for each column.
    table.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        for (column in WorksTableModel.Column.values()) {
          table.columnModel.getColumn(column.ordinal).preferredWidth = (table.width * column.widthPercentage).toInt()
        }
      }
    })
    return table
  }
}
