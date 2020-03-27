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
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorTextField
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.util.ArrayList
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * @see SqliteEvaluatorView
 */
class SqliteEvaluatorViewImpl(
  override val project: Project,
  override val tableView: TableView,
  private val schemaProvider: SchemaProvider
) : SqliteEvaluatorView {
  private val evaluatorPanel = SqliteEvaluatorPanel()
  override val component: JComponent = evaluatorPanel.root

  private val expandableEditor = ExpandableEditor(EditorTextField(project, AndroidSqlLanguage.INSTANCE.associatedFileType))

  private val listeners = ArrayList<SqliteEvaluatorView.Listener>()

  init {
    evaluatorPanel.controlsContainer.add(expandableEditor.collapsedEditor, BorderLayout.CENTER)
    evaluatorPanel.root.add(tableView.component, BorderLayout.CENTER)
    evaluatorPanel.evaluateButton.addActionListener { evaluateSqliteExpression() }

    evaluatorPanel.databaseComboBox.addActionListener { setSchemaFromSelectedItem() }

    val active = KeymapManager.getInstance().activeKeymap

    // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
    val shortcutsMultiline = active.getShortcuts("Console.Execute.Multiline")
    val keyStrokeMultiline = KeymapUtil.getKeyStroke(CustomShortcutSet(*shortcutsMultiline)) ?:
                             KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().menuShortcutKeyMask)

    val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(CustomShortcutSet(keyStrokeMultiline))
    evaluatorPanel.evaluateButton.toolTipText = "Run SQLite expression ($shortcutText)"

    val runStatementAction = DumbAwareAction.create { evaluateSqliteExpression() }

    runStatementAction.registerCustomShortcutSet(CustomShortcutSet(keyStrokeMultiline), expandableEditor.collapsedEditor)
    runStatementAction.registerCustomShortcutSet(CustomShortcutSet(keyStrokeMultiline), expandableEditor.expandedEditor)
  }

  override fun schemaChanged(database: SqliteDatabase) {
    // A fresh schema is taken from the schema provider each time the selected db changes in the combo box.
    // Therefore the only case we need to worry about is when the schema that changed belongs to the currently selected db.
    if ((evaluatorPanel.databaseComboBox.selectedItem as ComboBoxItem).database == database) {
      setSchemaFromSelectedItem()
    }
  }

  private fun evaluateSqliteExpression() {
    listeners.forEach {
      it.evaluateSqlActionInvoked(
        (evaluatorPanel.databaseComboBox.selectedItem as ComboBoxItem).database,
        expandableEditor.activeEditor.text
      )
    }
  }

  private fun setSchemaFromSelectedItem() {
    if (evaluatorPanel.databaseComboBox.selectedIndex < 0) return
    val database = (evaluatorPanel.databaseComboBox.selectedItem as ComboBoxItem).database
    val schema = schemaProvider.getSchema(database)

    val fileDocumentManager = FileDocumentManager.getInstance()
    fileDocumentManager.getFile(expandableEditor.collapsedEditor.document)?.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)
    fileDocumentManager.getFile(expandableEditor.expandedEditor.document)?.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)

    TransactionGuard.submitTransaction(project, Runnable {
      // since the schema has changed we need to drop psi caches to re-run reference resolution and highlighting in the editor text field.
      PsiManager.getInstance(project).dropPsiCaches()
    })
  }

  override fun addDatabase(database: SqliteDatabase, index: Int) {
    evaluatorPanel.databaseComboBox.insertItemAt(ComboBoxItem(database, database.name), index)
    if (evaluatorPanel.databaseComboBox.selectedIndex == -1) evaluatorPanel.databaseComboBox.selectedIndex = 0
  }

  override fun selectDatabase(database: SqliteDatabase) {
    // Avoid setting the item if it's already selected, so we don't trigger the action listener for now reason.
    val itemToSelect = ComboBoxItem(database, database.name)
    val currentlySelectedItem = evaluatorPanel.databaseComboBox.selectedItem
    if (itemToSelect != currentlySelectedItem) {
      evaluatorPanel.databaseComboBox.selectedItem = itemToSelect
    }
  }

  override fun removeDatabase(index: Int) {
    evaluatorPanel.databaseComboBox.removeItemAt(index)
  }

  override fun getActiveDatabase(): SqliteDatabase {
    return (evaluatorPanel.databaseComboBox.selectedItem as ComboBoxItem).database
  }

  override fun getSqliteStatement(): String {
    return expandableEditor.activeEditor.text
  }

  override fun addListener(listener: SqliteEvaluatorView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteEvaluatorView.Listener) {
    listeners.remove(listener)
  }

  override fun showSqliteStatement(sqliteStatement: String) {
    expandableEditor.activeEditor.text = sqliteStatement
  }

  internal data class ComboBoxItem(val database: SqliteDatabase, val name: String) {
    override fun toString() = name
  }
}