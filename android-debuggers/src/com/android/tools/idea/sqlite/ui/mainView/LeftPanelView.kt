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
package com.android.tools.idea.sqlite.ui.mainView

import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.renderers.SchemaTreeCellRenderer
import com.intellij.icons.AllIcons
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class LeftPanelView(private val mainView: DatabaseInspectorViewImpl) {
  private val rootPanel = JPanel(BorderLayout())
  private val tree = Tree()

  val component = rootPanel

  init {
    val northPanel = JPanel(FlowLayout(FlowLayout.LEFT))
    val centerPanel = JBScrollPane(tree)

    rootPanel.add(northPanel, BorderLayout.NORTH)
    rootPanel.add(centerPanel, BorderLayout.CENTER)

    setUpNorthPanel(northPanel)

    setUpSchemaTree(tree)
  }

  fun addDatabaseSchema(database: SqliteDatabase, schema: SqliteSchema, index: Int) {
    val treeModel = tree.model as DefaultTreeModel
    val root = treeModel.root as DefaultMutableTreeNode

    val schemaNode = DefaultMutableTreeNode(database)
    schema.tables.sortedBy { it.name }.forEach { table ->
      val tableNode = DefaultMutableTreeNode(table)
      table.columns.forEach { column -> tableNode.add(DefaultMutableTreeNode(column)) }
      schemaNode.add(tableNode)
    }

    treeModel.insertNodeInto(schemaNode, root, index)
    tree.expandPath(TreePath(schemaNode.path))
  }

  // TODO(b/149920358) handle error by recreating the view.
  fun updateDatabase(database: SqliteDatabase, diffOperations: List<SchemaDiffOperation>) {
    val treeModel = tree.model as DefaultTreeModel
    val databaseNode = findDatabaseNode(database)
    databaseNode.userObject = database

    for (diffOp in diffOperations) {
      when (diffOp) {
        is AddTable -> {
          addNewTableNode(treeModel, databaseNode, diffOp.indexedSqliteTable, diffOp.columns)
        }
        is AddColumns -> {
          val tableNode = findTableNode(databaseNode, diffOp.tableName) ?: error("No tree node found for table ${diffOp.tableName}")
          tableNode.userObject = diffOp.newTable
          addColumnsToTableNode(treeModel, tableNode, diffOp.columns)
        }
        is RemoveTable -> {
          val tableNode = findTableNode(databaseNode, diffOp.tableName) ?: error("No tree node found for table ${diffOp.tableName}")
          treeModel.removeNodeFromParent(tableNode)
        }
        is RemoveColumns -> {
          val tableNode = findTableNode(databaseNode, diffOp.tableName) ?: error("No tree node found for table ${diffOp.tableName}")
          tableNode.userObject = diffOp.newTable
          diffOp.columnsToRemove.map { findColumnNode(tableNode, it.name) }.forEach { treeModel.removeNodeFromParent(it) }
        }
      }
    }
  }

  fun removeDatabaseSchema(database: SqliteDatabase) {
    val treeModel = tree.model as DefaultTreeModel
    val databaseNode = findDatabaseNode(database)
    treeModel.removeNodeFromParent(databaseNode)
  }

  private fun setUpNorthPanel(northPanel: JPanel) {
    val closeDatabaseButton = CommonButton("Close db", AllIcons.Diff.Remove)
    closeDatabaseButton.toolTipText = "Close db"
    northPanel.add(closeDatabaseButton)

    closeDatabaseButton.addActionListener {
      val databaseToRemove = tree.selectionPaths?.mapNotNull { findDatabaseNode(it) }
      mainView.listeners.forEach { databaseToRemove?.forEach { database -> it.removeDatabaseActionInvoked(database) } }
    }

    val refreshSchema = CommonButton("Sync schema", AllIcons.Actions.Refresh)
    refreshSchema.toolTipText = "Sync schema"
    northPanel.add(refreshSchema)
    refreshSchema.addActionListener {
      mainView.listeners.forEach { it.refreshAllOpenDatabasesSchemaActionInvoked() }
    }

    val openSqliteEvaluatorButton = CommonButton("Run SQL", AllIcons.RunConfigurations.TestState.Run)
    openSqliteEvaluatorButton.toolTipText = "Open SQL evaluator tab"
    northPanel.add(openSqliteEvaluatorButton)

    openSqliteEvaluatorButton.addActionListener { mainView.listeners.forEach { it.openSqliteEvaluatorTabActionInvoked() } }
  }

  private fun setUpSchemaTree(tree: Tree) {
    tree.cellRenderer = SchemaTreeCellRenderer()
    val root = DefaultMutableTreeNode("Schemas")

    tree.model = DefaultTreeModel(root)
    tree.toggleClickCount = 0

    setUpSchemaTreeListeners(tree)
  }

  private fun setUpSchemaTreeListeners(tree: Tree) {
    // TODO(b/137731627) why do we have to do this manually? Check how is done in Device Explorer.
    val treeKeyAdapter = object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == KeyEvent.VK_ENTER) {
          fireAction(tree, event)
        }
      }
    }

    val treeDoubleClickListener = object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        fireAction(tree, event)
        return true
      }
    }

    tree.addKeyListener(treeKeyAdapter)
    treeDoubleClickListener.installOn(tree)
  }

  private fun fireAction(tree: Tree, e: InputEvent) {
    val lastPathComponent = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return

    val sqliteTable = lastPathComponent.userObject
    if (sqliteTable is SqliteTable) {
      val parentNode = lastPathComponent.parent as DefaultMutableTreeNode
      val database = parentNode.userObject as SqliteDatabase
      mainView.listeners.forEach { l -> l.tableNodeActionInvoked(database, sqliteTable) }
      e.consume()
    }
    else {
      val path = TreePath(lastPathComponent.path)
      if (tree.isExpanded(path)) {
        tree.collapsePath(path)
      }
      else {
        tree.expandPath(path)
      }
    }
  }

  /** Adds a child node to [databaseNode] for the table in [sqliteTableToAdd] at the specified index. */
  private fun addNewTableNode(treeModel: DefaultTreeModel, databaseNode: DefaultMutableTreeNode, sqliteTableToAdd: IndexedSqliteTable, sqliteColumnsToAdd: List<IndexedSqliteColumn>) {
    val (sqliteTable, index) = sqliteTableToAdd
    val newTableNode = DefaultMutableTreeNode(sqliteTable)
    sqliteColumnsToAdd.sortedBy { it.index }.map { it.sqliteColumn }.forEach { column -> newTableNode.add(DefaultMutableTreeNode(column)) }
    treeModel.insertNodeInto(newTableNode, databaseNode, index)
  }

  /** Add child nodes to [tableNode] for each column in [columnsToAdd] at the specified index */
  private fun addColumnsToTableNode(
    treeModel: DefaultTreeModel,
    tableNode: DefaultMutableTreeNode,
    columnsToAdd: List<IndexedSqliteColumn>
  ) {
    columnsToAdd.forEach {  indexedSqliteColumn ->
      val (sqliteColumn, index) = indexedSqliteColumn
      val newColumnNode = DefaultMutableTreeNode(sqliteColumn)
      treeModel.insertNodeInto(newColumnNode, tableNode, index)
    }
  }

  private fun findDatabaseNode(database: SqliteDatabase): DefaultMutableTreeNode {
    val root = tree.model.root as DefaultMutableTreeNode
    return root.children().asSequence()
      .map { it as DefaultMutableTreeNode }
      .first { it.userObject == database }
  }

  private fun findTableNode(databaseNode: DefaultMutableTreeNode, tableName: String): DefaultMutableTreeNode? {
    return databaseNode.children().asSequence()
      .map { it as DefaultMutableTreeNode }
      .filter { it.userObject is SqliteTable }
      .firstOrNull { (it.userObject as SqliteTable).name == tableName }
  }

  private fun findColumnNode(databaseNode: DefaultMutableTreeNode, columnName: String): DefaultMutableTreeNode? {
    return databaseNode.children().asSequence()
      .map { it as DefaultMutableTreeNode }
      .filter { it.userObject is SqliteColumn }
      .firstOrNull { (it.userObject as SqliteColumn).name == columnName }
  }

  private fun findDatabaseNode(treePath: TreePath): SqliteDatabase {
    var currentPath: TreePath? = treePath
    while (currentPath != null) {
      val userObject = (currentPath.lastPathComponent as DefaultMutableTreeNode).userObject
      if (userObject is SqliteDatabase)
        return userObject
      currentPath = currentPath.parentPath
    }

    throw NoSuchElementException("$treePath")
  }
}