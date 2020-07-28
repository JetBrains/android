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
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.sqlLanguage.SqliteSchemaContext
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.psi.PsiManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.util.ArrayList
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * @see SqliteEvaluatorView
 */
class SqliteEvaluatorViewImpl(
  override val project: Project,
  override val tableView: TableView,
  private val schemaProvider: SchemaProvider
) : SqliteEvaluatorView {

  private val threeComponentsSplitter = ThreeComponentsSplitter(project)
  override val component: JComponent = threeComponentsSplitter

  private val databaseComboBox = ComboBox<SqliteDatabaseId>()
  private val editorTextField = EditorTextFieldProvider.getInstance().getEditorField(
    AndroidSqlLanguage.INSTANCE,
    project,
    listOf(EditorCustomization { editor -> editor.setBorder(JBUI.Borders.empty()) })
  )

  private val listeners = ArrayList<SqliteEvaluatorView.Listener>()

  private val runButton = JButton("Run")
  private var evaluateSqliteStatementEnabled = false

  init {
    val evaluatorPanel = JPanel(BorderLayout())
    val controlsPanel = JPanel(BorderLayout())

    evaluatorPanel.add(editorTextField, BorderLayout.CENTER)
    evaluatorPanel.add(controlsPanel, BorderLayout.SOUTH)

    threeComponentsSplitter.dividerWidth = 0
    threeComponentsSplitter.firstSize = JBUI.scale(100)
    threeComponentsSplitter.orientation = true
    threeComponentsSplitter.firstComponent = evaluatorPanel
    threeComponentsSplitter.lastComponent = tableView.component
    threeComponentsSplitter.invalidate()
    threeComponentsSplitter.repaint()

    evaluatorPanel.border = JBUI.Borders.empty(6)
    tableView.component.border = IdeBorderFactory.createBorder(SideBorder.TOP)

    evaluatorPanel.background = editorTextField.background
    controlsPanel.background = editorTextField.background

    val active = KeymapManager.getInstance().activeKeymap

    // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
    val shortcutsMultiline = active.getShortcuts("Console.Execute.Multiline")
    val keyStrokeMultiline = KeymapUtil.getKeyStroke(CustomShortcutSet(*shortcutsMultiline)) ?:
                             KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().menuShortcutKeyMask)

    val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(CustomShortcutSet(keyStrokeMultiline))
    runButton.toolTipText = "Run SQLite expression ($shortcutText)"
    runButton.isEnabled = false
    runButton.addActionListener { evaluateSqliteExpression() }
    runButton.name = "run-button"

    val runStatementAction = DumbAwareAction.create { evaluateSqliteExpression() }

    editorTextField.name = "editor"
    editorTextField.setPlaceholder("Enter query")
    runStatementAction.registerCustomShortcutSet(CustomShortcutSet(keyStrokeMultiline), editorTextField)

    databaseComboBox.addActionListener {
      setSchemaFromSelectedItem()
      val sqliteDatabaseId = databaseComboBox.selectedItem as? SqliteDatabaseId ?: return@addActionListener

      listeners.forEach {
        it.onDatabaseSelected(sqliteDatabaseId)
      }
    }

    databaseComboBox.setMinimumAndPreferredWidth(JBUI.scale(300))
    databaseComboBox.renderer = object : ColoredListCellRenderer<SqliteDatabaseId?>() {
      override fun customizeCellRenderer(
        list: JList<out SqliteDatabaseId?>,
        sqliteDatabase: SqliteDatabaseId?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
      ) {
        if (sqliteDatabase != null) {
          icon = StudioIcons.DatabaseInspector.DATABASE
          append(sqliteDatabase.name)
        } else {
          icon = null
          append(DatabaseInspectorBundle.message("no.databases.available"))
        }
      }
    }

    val myDocumentListener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        listeners.forEach { it.sqliteStatementTextChangedInvoked(event.document.text) }
      }
    }

    editorTextField.document.addDocumentListener(myDocumentListener)

    controlsPanel.add(runButton, BorderLayout.EAST)
    controlsPanel.add(databaseComboBox, BorderLayout.WEST)
  }

  override fun schemaChanged(databaseId: SqliteDatabaseId) {
    // A fresh schema is taken from the schema provider each time the selected db changes in the combo box.
    // Therefore the only case we need to worry about is when the schema that changed belongs to the currently selected db.
    if ((databaseComboBox.selectedItem as SqliteDatabaseId) == databaseId) {
      setSchemaFromSelectedItem()
    }
  }

  override fun setRunSqliteStatementEnabled(enabled: Boolean) {
    evaluateSqliteStatementEnabled = enabled
    runButton.isEnabled = enabled
  }

  private fun evaluateSqliteExpression() {
    if (!evaluateSqliteStatementEnabled) return

    listeners.forEach {
      it.evaluateCurrentStatement()
    }
  }

  private fun setSchemaFromSelectedItem() {
    if (databaseComboBox.selectedIndex < 0) return
    val database = (databaseComboBox.selectedItem as SqliteDatabaseId)
    val schema = schemaProvider.getSchema(database)

    val fileDocumentManager = FileDocumentManager.getInstance()
    fileDocumentManager.getFile(editorTextField.document)?.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)

    // since the schema has changed we need to drop psi caches to re-run reference resolution and highlighting in the editor text field.
    ApplicationManager.getApplication().invokeLaterOnWriteThread { PsiManager.getInstance(project).dropPsiCaches() }
  }

  override fun setDatabases(databaseIds: List<SqliteDatabaseId>, selected: SqliteDatabaseId?) {
    databaseComboBox.removeAllItems()
    databaseComboBox.isEnabled = databaseIds.isNotEmpty()
    for (database in databaseIds) {
      databaseComboBox.addItem(database)
    }

    // Avoid setting the item if it's already selected, so we don't trigger the action listener for no reason.
    if (databaseComboBox.selectedItem != selected) {
      databaseComboBox.selectedItem = selected
    }
  }

  override fun addListener(listener: SqliteEvaluatorView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteEvaluatorView.Listener) {
    listeners.remove(listener)
  }

  override fun showSqliteStatement(sqliteStatement: String) {
    editorTextField.text = sqliteStatement
  }
}