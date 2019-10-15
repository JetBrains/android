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

import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.sqlLanguage.SqliteSchemaContext
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import java.awt.BorderLayout
import java.util.ArrayList
import javax.swing.JComponent

/**
 * @see SqliteEvaluatorView
 */
class SqliteEvaluatorViewImpl(override val project: Project, private val schemaProvider: SchemaProvider) : SqliteEvaluatorView {
  private val evaluatorPanel = SqliteEvaluatorPanel()
  override val component: JComponent = evaluatorPanel.root
  override val tableView = TableViewImpl()

  private val editorTextField = LanguageTextField(AndroidSqlLanguage.INSTANCE, project, "")

  private val listeners = ArrayList<SqliteEvaluatorViewListener>()

  init {
    evaluatorPanel.controlsContainer.add(editorTextField, BorderLayout.CENTER)
    evaluatorPanel.root.add(tableView.component, BorderLayout.CENTER)
    evaluatorPanel.evaluateButton.addActionListener {
      listeners.forEach {
        it.evaluateSqlActionInvoked((evaluatorPanel.schemaComboBox.selectedItem as ComboBoxItem).database, editorTextField.text)
      }
    }

    evaluatorPanel.schemaComboBox.addActionListener { setSchemaFromSelectedItem() }
  }

  private fun setSchemaFromSelectedItem() {
    val database = (evaluatorPanel.schemaComboBox.selectedItem as ComboBoxItem).database
    val schema = schemaProvider.getSchema(database)
    FileDocumentManager.getInstance().getFile(editorTextField.document)?.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)
  }

  override fun addDatabase(database: SqliteDatabase, index: Int) {
    evaluatorPanel.schemaComboBox.insertItemAt(ComboBoxItem(database, database.name), index)
    if (evaluatorPanel.schemaComboBox.selectedIndex == -1) evaluatorPanel.schemaComboBox.selectedIndex = 0
    setSchemaFromSelectedItem()
  }

  override fun selectDatabase(database: SqliteDatabase) {
    evaluatorPanel.schemaComboBox.selectedItem = database
  }

  override fun removeDatabase(index: Int) {
    evaluatorPanel.schemaComboBox.removeItemAt(index)
  }

  override fun addListener(listener: SqliteEvaluatorViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteEvaluatorViewListener) {
    listeners.remove(listener)
  }

  override fun showSqliteStatement(sqliteStatement: String) {
    editorTextField.text = sqliteStatement
  }

  internal data class ComboBoxItem(val database: SqliteDatabase, val name: String) {
    override fun toString() = name
  }
}