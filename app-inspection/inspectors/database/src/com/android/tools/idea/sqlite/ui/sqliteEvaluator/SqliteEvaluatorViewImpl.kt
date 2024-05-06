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

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.stdui.Chunk
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.adtui.stdui.LabelData
import com.android.tools.adtui.stdui.NewLineChunk
import com.android.tools.adtui.stdui.TextChunk
import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.sqlLanguage.SqliteSchemaContext
import com.android.tools.idea.sqlite.ui.notifyError
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.icons.AllIcons
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
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.util.ArrayList
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.LayoutFocusTraversalPolicy

/** @see SqliteEvaluatorView */
class SqliteEvaluatorViewImpl(
  override val project: Project,
  override val tableView: TableView,
  private val schemaProvider: SchemaProvider,
  private val dropPsiCaches: () -> Unit = {
    ApplicationManager.getApplication().invokeLaterOnWriteThread {
      PsiManager.getInstance(project).dropPsiCaches()
    }
  }
) : SqliteEvaluatorView {

  private val splitterPanel = OnePixelSplitter(true)
  private val bottomPanel = JPanel(BorderLayout())
  override val component: JComponent = splitterPanel

  private val databaseComboBox = ComboBox<SqliteDatabaseId>()
  private val editorTextField =
    EditorTextFieldProvider.getInstance()
      .getEditorField(
        AndroidSqlLanguage.INSTANCE,
        project,
        listOf(EditorCustomization { editor -> editor.setBorder(JBUI.Borders.empty()) })
      )

  private val listeners = ArrayList<SqliteEvaluatorView.Listener>()

  private val queryHistoryButton = CommonButton(AllIcons.Vcs.History)
  private val runButton = JButton("Run")
  private var evaluateSqliteStatementEnabled = false

  private val queryHistoryView = QueryHistoryView(editorTextField)

  init {
    val topPanel = JPanel(BorderLayout())
    val controlsPanel = JPanel(BorderLayout())

    controlsPanel.layout = BoxLayout(controlsPanel, BoxLayout.LINE_AXIS)

    topPanel.add(editorTextField, BorderLayout.CENTER)
    topPanel.add(controlsPanel, BorderLayout.SOUTH)

    // Override the splitter's custom traversal policy back to the default, because the custom
    // policy prevents from tabbing
    // across the components.
    splitterPanel.apply {
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
      isFocusCycleRoot = false
      proportion = 0.3f
      firstComponent = topPanel
      secondComponent = bottomPanel
    }

    topPanel.border = JBUI.Borders.empty(6)
    controlsPanel.border = JBUI.Borders.empty(6, 0, 0, 0)
    bottomPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)

    topPanel.background = primaryContentBackground
    controlsPanel.background = primaryContentBackground

    controlsPanel.add(databaseComboBox)
    controlsPanel.add(Box.createRigidArea(JBDimension.size(Dimension(5, 0))))
    controlsPanel.add(queryHistoryButton)
    controlsPanel.add(Box.createHorizontalGlue())
    controlsPanel.add(runButton)

    databaseComboBox.apply {
      addActionListener {
        setSchemaFromSelectedItem()
        val sqliteDatabaseId =
          databaseComboBox.selectedItem as? SqliteDatabaseId ?: return@addActionListener

        listeners.forEach { it.onDatabaseSelected(sqliteDatabaseId) }
      }

      setMinimumAndPreferredWidth(JBUI.scale(300))
      maximumSize = JBUI.size(300, databaseComboBox.preferredSize.height)
      renderer =
        object : ColoredListCellRenderer<SqliteDatabaseId?>() {
          override fun customizeCellRenderer(
            list: JList<out SqliteDatabaseId?>,
            sqliteDatabase: SqliteDatabaseId?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
          ) {
            if (sqliteDatabase != null) {
              icon =
                when (sqliteDatabase) {
                  is SqliteDatabaseId.LiveSqliteDatabaseId -> StudioIcons.DatabaseInspector.DATABASE
                  is SqliteDatabaseId.FileSqliteDatabaseId ->
                    StudioIcons.DatabaseInspector.DATABASE_OFFLINE
                }
              append(sqliteDatabase.name)
            } else {
              icon = null
              append(DatabaseInspectorBundle.message("no.databases.available"))
            }
          }
        }
    }

    queryHistoryButton.apply {
      disabledIcon = IconLoader.getDisabledIcon(AllIcons.Vcs.History)
      toolTipText = "Show query history"
      addActionListener {
        queryHistoryView.show(
          component,
          queryHistoryButton.x + queryHistoryButton.width / 2,
          topPanel.height - topPanel.border.getBorderInsets(controlsPanel).bottom
        )
      }
    }

    // shortcuts
    val active = KeymapManager.getInstance().activeKeymap
    // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
    val shortcutsMultiline = active.getShortcuts("Console.Execute.Multiline")
    val keyStrokeMultiline =
      KeymapUtil.getKeyStroke(CustomShortcutSet(*shortcutsMultiline))
        ?: KeyStroke.getKeyStroke(
          KeyEvent.VK_ENTER,
          Toolkit.getDefaultToolkit().menuShortcutKeyMask
        )
    val shortcutText =
      KeymapUtil.getFirstKeyboardShortcutText(CustomShortcutSet(keyStrokeMultiline))

    runButton.apply {
      toolTipText = "Run SQLite expression ($shortcutText)"
      isEnabled = false
      addActionListener { evaluateSqliteExpression() }
      name = "run-button"
      background = primaryContentBackground
    }

    editorTextField.apply {
      background = primaryContentBackground
      name = "editor"
      setPlaceholder("Enter query...")

      DumbAwareAction.create { evaluateSqliteExpression() }
        .registerCustomShortcutSet(CustomShortcutSet(keyStrokeMultiline), editorTextField)

      document.addDocumentListener(
        object : DocumentListener {
          override fun documentChanged(event: DocumentEvent) {
            listeners.forEach { it.sqliteStatementTextChangedInvoked(event.document.text) }
          }
        }
      )
    }
  }

  override fun schemaChanged(databaseId: SqliteDatabaseId) {
    // A fresh schema is taken from the schema provider each time the selected db changes in the
    // combo box.
    // Therefore the only case we need to worry about is when the schema that changed belongs to the
    // currently selected db.
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

    listeners.forEach { it.evaluateCurrentStatement() }
  }

  private fun setSchemaFromSelectedItem() {
    if (databaseComboBox.selectedIndex < 0) return
    val database = (databaseComboBox.selectedItem as SqliteDatabaseId)
    val schema = schemaProvider.getSchema(database)

    val fileDocumentManager = FileDocumentManager.getInstance()
    fileDocumentManager
      .getFile(editorTextField.document)
      ?.putUserData(SqliteSchemaContext.SQLITE_SCHEMA_KEY, schema)

    // since the schema has changed we need to drop psi caches to re-run reference resolution and
    // highlighting in the editor text field.
    dropPsiCaches()
  }

  override fun setDatabases(databaseIds: List<SqliteDatabaseId>, selected: SqliteDatabaseId?) {
    databaseComboBox.removeAllItems()
    databaseComboBox.isEnabled = databaseIds.isNotEmpty()
    for (database in databaseIds) {
      databaseComboBox.addItem(database)
    }

    // Avoid setting the item if it's already selected, so we don't trigger the action listener for
    // no reason.
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

  override fun reportError(message: String, t: Throwable?) {
    notifyError(message, t)
  }

  override fun setQueryHistory(queries: List<String>) {
    queryHistoryView.setQueryHistory(queries)
  }

  override fun showMessagePanel(message: String) {
    val chunks = mutableListOf<Chunk>()
    message.split("\n").forEach {
      chunks.add(TextChunk(it))
      chunks.add(NewLineChunk)
    }

    val enterOfflineModePanel = EmptyStatePanel(LabelData(*chunks.dropLast(1).toTypedArray()))
    enterOfflineModePanel.name = "message-panel"

    resetBottomPanelAndAddView(enterOfflineModePanel)
  }

  override fun showTableView() {
    resetBottomPanelAndAddView(tableView.component)
  }

  private fun resetBottomPanelAndAddView(component: JComponent) {
    bottomPanel.removeAll()
    bottomPanel.layout = BorderLayout()
    bottomPanel.add(component, BorderLayout.CENTER)
    bottomPanel.revalidate()
    bottomPanel.repaint()
  }
}
