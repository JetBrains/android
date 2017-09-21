/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteModel
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.ArrayList
import java.util.Vector
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SqliteViewImpl(
  project: Project,
  private val model: SqliteModel,
  fileEditor: FileEditor
) : SqliteView {
  private val listeners = ArrayList<SqliteViewListener>()
  private val workBench: WorkBench<SqliteViewContext>
  private val viewContext = SqliteViewContext()
  private var panel: SqliteEditorPanel? = null
  private val columnClass = SqliteColumnValue::class.java
  private val tableModel: DefaultTableModel by lazy {
    object : DefaultTableModel() {
      override fun getColumnClass(columnIndex: Int): Class<*> {
        // We need this so that our custom default cell renderer is active
        return columnClass
      }
    }
  }

  init {
    workBench = WorkBench(project, "Sqlite", fileEditor)
    Disposer.register(fileEditor, workBench)
  }

  override val component: JComponent
    get() = workBench

  override fun addListener(listener: SqliteViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteViewListener) {
    listeners.remove(listener)
  }

  override fun setup() {}

  override fun startLoading(text: String) {
    workBench.setLoadingText(text)
  }

  override fun stopLoading() {
    val newPanel = SqliteEditorPanel()
    newPanel.deviceIdText.text = model.sqliteFileId.deviceId
    newPanel.devicePathText.text = model.sqliteFileId.devicePath

    // Setup status text with link to Schema tool window
    newPanel.resultSetTable.emptyText.clear()
    newPanel.resultSetTable.emptyText.appendText("Double click on a table in the ")
    newPanel.resultSetTable.emptyText.appendText("Schema", SimpleTextAttributes.LINK_ATTRIBUTES) {
      this.activateSchemaToolWindow()
    }
    newPanel.resultSetTable.emptyText.appendText(" ToolWindow")

    val definitions = ArrayList<ToolWindowDefinition<SqliteViewContext>>()
    definitions.add(SchemaPanelToolContent.getDefinition())
    workBench.init(newPanel.mainPanel, viewContext, definitions)

    this.panel = newPanel
  }

  private fun activateSchemaToolWindow() {
    viewContext.schemaTree?.let { tree ->
      if (tree.selectionCount == 0) {
        // Note: This assume root node is a reasonable node to focus
        tree.model.root?.let { tree.setSelectionRow(0)}
      }
      tree.requestFocusInWindow()
    }
  }

  override fun displaySchema(schema: SqliteSchema) {
    viewContext.schema = schema
    setupSchemaTree()
  }

  private fun setupSchemaTree() {
    viewContext.schemaTree?.let { tree ->
      tree.cellRenderer = SchemaTreeCellRenderer()
      val root = DefaultMutableTreeNode("Tables")
      val schema = viewContext.schema
      schema?.let {
        it.tables.forEach { table ->
          val tableNode = DefaultMutableTreeNode(table)
          table.columns.forEach { column ->
            val columnNode = DefaultMutableTreeNode(column)
            tableNode.add(columnNode)
          }
          root.add(tableNode)
        }
      }
      tree.model = DefaultTreeModel(root)
      tree.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ENTER) {
            fireAction(tree, e)
          }
          super.keyPressed(e)
        }
      })
      tree.toggleClickCount = 0
      tree.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) {
            fireAction(tree, e)
          }
          super.mouseClicked(e)
        }
      })
    }
  }

  private fun fireAction(tree: Tree, e: InputEvent) {
    tree.selectionPath?.lastPathComponent?.let {
      if (it is DefaultMutableTreeNode) {
        val userObject = it.userObject
        if (userObject is SqliteTable) {
          listeners.forEach { l -> l.tableNodeActionInvoked(userObject) }
          e.consume()
        }
      }
    }
  }

  override fun startTableLoading(table: SqliteTable) {
    // Delete table contents (row and columns)
    setupResultSetTitle("Contents of table '${table.name}'")
    //panel?.resultSetPane?.let { it.isVisible = true }
    panel?.resultSetTable?.let { jbTable ->
      setupResultSetTable(jbTable)
      tableModel.rowCount = 0
      tableModel.columnCount = 0
      jbTable.setPaintBusy(true)
    }
  }

  override fun showTableColumns(columns: List<SqliteColumn>) {
    panel?.resultSetTable?.let { table ->
      columns.forEach { c ->
        tableModel.addColumn(c.name)
      }

      setResultSetTableColumns(table)
    }
  }

  private fun setupResultSetTable(table: JBTable) {
    if (table.model != tableModel) {
      table.model = tableModel
      table.setDefaultRenderer(columnClass, ResultSetTreeCellRenderer())
      // Turn off JTable's auto resize so that JScrollPane will show a horizontal
      // scroll bar.
      table.autoResizeMode = JTable.AUTO_RESIZE_OFF
      table.emptyText.text = "Table is empty"
    }
  }

  private fun setupResultSetTitle(title: String) {
    panel?.resultSetTitleLabel?.let { it.text = title }
  }

  private fun setResultSetTableColumns(table: JBTable) {
    val headerRenderer = ResultSetTreeHeaderRenderer(table.tableHeader.defaultRenderer)
    val width = Math.max(JBUI.scale(50), (table.parent.width - JBUI.scale(10)) / table.columnModel.columnCount)
    for (index in 0 until table.columnModel.columnCount) {
      val column = table.columnModel.getColumn(index)
      column.preferredWidth = width
      column.headerRenderer = headerRenderer
    }
  }

  override fun showTableRowBatch(rows: List<SqliteRow>) {
    panel?.resultSetTable?.let {
      rows.forEach { row ->
        val values = Vector<SqliteColumnValue>()
        row.values.forEach { values.addElement(it) }
        tableModel.addRow(values)
      }
    }
  }

  override fun stopTableLoading() {
    panel?.resultSetTable?.setPaintBusy(false)
  }

  override fun reportErrorRelatedToService(service: SqliteService, message: String, t: Throwable) {
    var errorMessage = message
    t.message?.let {
      errorMessage += ": " + t.message
    }

    workBench.loadingStopped(errorMessage)
  }

  override fun reportErrorRelatedToTable(table: SqliteTable, message: String, t: Throwable) {
    reportError(message, t)
  }

  private fun reportError(message: String, t: Throwable) {
    if (t is CancellationException) {
      return
    }

    var errorMessage = message
    t.message?.let {
      errorMessage += ": " + t.message
    }

    val notification = Notification("Sqlite Viewer",
        "Sqlite Viewer",
        errorMessage,
        NotificationType.WARNING)

    ApplicationManager.getApplication().invokeLater { Notifications.Bus.notify(notification) }
  }

  class SchemaPanelToolContent : ToolContent<SqliteViewContext> {
    private val schemaPanel = SqliteSchemaPanel()

    override fun getComponent(): JComponent {
      return schemaPanel.component
    }

    override fun dispose() {
    }

    /**
     * Initialize the UI from the passed in [SqliteViewContext]
     */
    override fun setToolContext(toolContext: SqliteViewContext?) {
      toolContext?.schemaTree = schemaPanel.tree
    }

    companion object {
      fun getDefinition(): ToolWindowDefinition<SqliteViewContext> {
        return ToolWindowDefinition(
          "Schema",
          AllIcons.Nodes.DataSchema,
          "SCHEMA",
          Side.LEFT,
          Split.TOP,
          AutoHide.DOCKED,
          { SchemaPanelToolContent() }
        )
      }
    }
  }

  class SqliteViewContext {
    var schema: SqliteSchema? = null
    var schemaTree: Tree? = null
  }
}