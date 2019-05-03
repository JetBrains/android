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
package com.android.tools.idea.sqlite.ui.sqliteEvaluator

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.ui.reportError
import com.android.tools.idea.sqlite.ui.setResultSetTableColumns
import com.android.tools.idea.sqlite.ui.setupResultSetTable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import java.util.ArrayList
import java.util.Vector
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * A dialog that can be used to run sql queries and updates.
 */
@UiThread
class SqliteEvaluatorDialog(
  project: Project?,
  canBeParent: Boolean
) : DialogWrapper(project, canBeParent), SqliteEvaluatorView {
  private val columnClass = SqliteColumnValue::class.java
  private val tableModel: DefaultTableModel by lazy {
    object : DefaultTableModel() {
      override fun getColumnClass(columnIndex: Int): Class<*> {
        // We need this so that our custom default cell renderer is active
        return columnClass
      }
    }
  }

  private val mainPanel: JPanel
  private val evaluatorPanel: SqliteEvaluatorPanel

  private val listeners = ArrayList<SqliteEvaluatorViewListener>()

  init {
    mainPanel = BorderLayoutPanel()

    evaluatorPanel = SqliteEvaluatorPanel()
    mainPanel.add(evaluatorPanel.mainPanel)

    isModal = false
    title = "SQL evaluator"
    setOKButtonText("Evaluate")
    setCancelButtonText("Close")

    init()
  }

  override fun addListener(listener: SqliteEvaluatorViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteEvaluatorViewListener) {
    listeners.remove(listener)
  }

  override fun resetView() {
    tableModel.dataVector.clear()
    tableModel.rowCount = 0
    tableModel.columnCount = 0
  }

  override fun requestFocus() {
    toFront()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return evaluatorPanel.textField
  }

  override fun doOKAction() {
    listeners.forEach { it.evaluateSqlActionInvoked(evaluatorPanel.textField.text) }
  }

  override fun doCancelAction() {
    listeners.forEach { it.sessionClosed() }
    super.doCancelAction()
  }

  override fun createCenterPanel() = mainPanel

  override fun dispose() {
    super.dispose()
    mainPanel.removeAll()
  }

  override fun startTableLoading(tableName: String?) {
    evaluatorPanel.table.setupResultSetTable(tableModel, columnClass)
    tableModel.rowCount = 0
    tableModel.columnCount = 0
    evaluatorPanel.table.setPaintBusy(true)
  }

  override fun showTableColumns(columns: List<SqliteColumn>) {
    columns.forEach { c ->
      tableModel.addColumn(c.name)
    }

    evaluatorPanel.table.setResultSetTableColumns()
  }

  override fun showTableRowBatch(rows: List<SqliteRow>) {
    rows.forEach { row ->
      val values = Vector<SqliteColumnValue>()
      row.values.forEach { values.addElement(it) }
      tableModel.addRow(values)
    }
  }

  override fun stopTableLoading() {
    evaluatorPanel.table.setPaintBusy(false)
  }

  override fun reportErrorRelatedToTable(tableName: String?, message: String, t: Throwable) {
    reportError(message, t)
  }
}