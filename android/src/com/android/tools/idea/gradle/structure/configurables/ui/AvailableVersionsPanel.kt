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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.structure.configurables.ui.properties.renderTo
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.getText
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel.SINGLE_SELECTION
import javax.swing.table.TableCellRenderer

internal class AvailableVersionsPanel(notifyVersionSelectionChanged: Consumer<ParsedValue<GradleVersion>>) : JPanel(BorderLayout()) {
  private val versionsTable: TableView<ParsedValue<GradleVersion>> = TableView()

  init {
    versionsTable.setShowGrid(false)
    versionsTable.setSelectionMode(SINGLE_SELECTION)
    val cellRenderer = object : ColoredTableCellRenderer() {
      init {
        font = versionsTable.font
      }
      override fun customizeCellRenderer(table: JTable?, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        @Suppress("UNCHECKED_CAST")
        (value as ParsedValue<GradleVersion>?)?.renderTo(this.toRenderer(), { toString() }, mapOf())
      }
    }
    versionsTable.listTableModel.columnInfos = arrayOf(
      object : ColumnInfo<ParsedValue<GradleVersion>, ParsedValue<GradleVersion>>("Versions") {
        override fun valueOf(version: ParsedValue<GradleVersion>): ParsedValue<GradleVersion> = version
        override fun getRenderer(item: ParsedValue<GradleVersion>?): TableCellRenderer? = cellRenderer
      })

    versionsTable.selectionModel.addListSelectionListener {
      notifyVersionSelectionChanged.accept(versionsTable.selectedObject ?: ParsedValue.NotSet)
    }

    val scrollPane = createScrollPane(versionsTable)
    add(scrollPane, BorderLayout.CENTER)
  }

  fun setVersions(versions: List<ParsedValue<GradleVersion>>) {
    versionsTable.listTableModel.items = versions
    if (versions.isNotEmpty()) {
      versionsTable.selectionModel.setSelectionInterval(0, 0)
    }
  }

  fun setEmptyText(text: String) {
    versionsTable.emptyText.text = text
  }

  fun clear() = setVersions(listOf())
}
