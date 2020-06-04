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

import com.android.tools.adtui.HoverRowTable
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * View class for the WorkManger Inspector Tab with a table of all active works.
 */
class WorkManagerInspectorTab(private val client: WorkManagerInspectorClient) {
  private val root = JPanel(VerticalLayout(0))

  val component: JComponent = root

  init {
    root.add(JScrollPane(buildWorksTable()))
  }

  private fun buildWorksTable(): JBTable {
    val model = WorksTableModel(client)
    val table: JBTable = HoverRowTable(model)

    table.autoCreateRowSorter = true

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
