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
package com.android.tools.idea.sqlite.ui.mainView

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
import com.android.tools.idea.sqlite.ui.ResultSetView
import com.android.tools.idea.sqlite.ui.renderers.SchemaTreeCellRenderer
import com.android.tools.idea.sqlite.ui.reportError
import com.android.tools.idea.sqlite.ui.setResultSetTableColumns
import com.android.tools.idea.sqlite.ui.setupResultSetTable
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.ArrayList
import java.util.Vector
import javax.swing.JComponent
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

  override val tableView = TableViewResultSetImpl()

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

  override fun setUp() { }

  override fun startLoading(text: String) {
    workBench.setLoadingText(text)
  }

  override fun stopLoading() {
    val panel = SqliteEditorPanel()
    this.panel = panel

    panel.deviceIdText.text = model.sqliteFileId.deviceId
    panel.devicePathText.text = model.sqliteFileId.devicePath

    resetView()

    val definitions = ArrayList<ToolWindowDefinition<SqliteViewContext>>()
    definitions.add(SchemaPanelToolContent.getDefinition())
    workBench.init(panel.mainPanel, viewContext, definitions)

    panel.openSqlEvalDialog.addActionListener { listeners.forEach{ it.openSqliteEvaluatorActionInvoked() } }
  }

  override fun resetView() {
    tableModel.dataVector.clear()
    tableModel.rowCount = 0
    tableModel.columnCount = 0

    panel?.resultSetTable?.emptyText?.clear()
    panel?.resultSetTitleLabel?.text = ""
    panel?.resultSetTable?.emptyText?.appendText("Double click on a table in the ")
    panel?.resultSetTable?.emptyText?.appendText("Schema", SimpleTextAttributes.LINK_ATTRIBUTES) {
      activateSchemaToolWindow()
    }
    panel?.resultSetTable?.emptyText?.appendText(" ToolWindow")
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

  private fun setupResultSetTitle(title: String) {
    panel?.resultSetTitleLabel?.let { it.text = title }
  }

  override fun reportErrorRelatedToService(service: SqliteService, message: String, t: Throwable) {
    var errorMessage = message
    t.message?.let {
      errorMessage += ": " + t.message
    }

    workBench.loadingStopped(errorMessage)
  }

  inner class TableViewResultSetImpl : ResultSetView {
    override fun startTableLoading(tableName: String?) {
      // We know that in SqliteViewImpl the table name will never be null,
      // because each table shown here corresponds to a real table in the database.
      assert(tableName != null)

      setupResultSetTitle("Contents of table: '$tableName'")
      panel?.resultSetTable?.let { jbTable ->
        jbTable.setupResultSetTable(tableModel, columnClass)
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

        table.setResultSetTableColumns()
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

    override fun reportErrorRelatedToTable(tableName: String?, message: String, t: Throwable) {
      reportError(message, t)
    }
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