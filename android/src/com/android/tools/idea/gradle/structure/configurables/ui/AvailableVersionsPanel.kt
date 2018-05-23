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
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.ListSelectionModel.SINGLE_SELECTION

internal class AvailableVersionsPanel(notifyVersionSelectionChanged: Consumer<String?>) : JPanel(BorderLayout()) {
  private val versionsTable: TableView<GradleVersion> = TableView()

  init {
    versionsTable.setShowGrid(false)
    versionsTable.setSelectionMode(SINGLE_SELECTION)
    versionsTable.listTableModel.columnInfos = arrayOf(
      object : ColumnInfo<GradleVersion, String>("Versions") {
        override fun valueOf(version: GradleVersion): String? = version.toString()
      })

    versionsTable.selectionModel.addListSelectionListener {
      notifyVersionSelectionChanged.accept(versionsTable.selectedObject?.toString())
    }

    val scrollPane = createScrollPane(versionsTable)
    add(scrollPane, BorderLayout.CENTER)
  }

  fun setVersions(versions: List<GradleVersion>) {
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


