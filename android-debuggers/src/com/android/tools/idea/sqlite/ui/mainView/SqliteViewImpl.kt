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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.controllers.TabId
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.renderers.SchemaTreeCellRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.UIBundle
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.UiDecorator
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

@UiThread
class SqliteViewImpl(
  project: Project,
  parentDisposable: Disposable
) : SqliteView {
  private val viewContext = SqliteViewContext()
  private val listeners = mutableListOf<SqliteViewListener>()

  private val rootPanel = JPanel()
  private val workBench: WorkBench<SqliteViewContext> = WorkBench(project, "Sqlite", null, parentDisposable)
  private var sqliteEditorPanel = SqliteEditorPanel()
  private val defaultUiPanel = DefaultUiPanel()
  private val tabs = JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.getInstance(project), project)

  override val component: JComponent = rootPanel

  private val openTabs = mutableMapOf<TabId, TabInfo>()

  init {
    val definitions = mutableListOf<ToolWindowDefinition<SqliteViewContext>>()
    definitions.add(createToolWindowDefinition())
    workBench.init(sqliteEditorPanel.mainPanel, viewContext, definitions, false)

    rootPanel.layout = OverlayLayout(rootPanel)
    rootPanel.add(defaultUiPanel.rootPanel)
    rootPanel.add(workBench)
    workBench.isVisible = false

    defaultUiPanel.label.font = AdtUiUtils.EMPTY_TOOL_WINDOW_FONT
    defaultUiPanel.label.foreground = UIUtil.getInactiveTextColor()

    val openSqliteEvaluatorButton = CommonButton("Open SQLite evaluator", AllIcons.Actions.Search)
    openSqliteEvaluatorButton.toolTipText = "Open SQLite evaluator"
    sqliteEditorPanel.headerPanel.add(openSqliteEvaluatorButton)

    openSqliteEvaluatorButton.addActionListener { listeners.forEach { it.openSqliteEvaluatorTabActionInvoked() } }

    tabs.apply {
      isTabDraggingEnabled = true
      setUiDecorator { UiDecorator.UiDecoration(null, JBUI.insets(4, 10)) }
      addTabMouseListener(TabMouseListener())
    }

    sqliteEditorPanel.tabsRoot.add(tabs)

    setUpSqliteSchemaTree()
  }

  private fun setUpSqliteSchemaTree() {
    // TODO(b/137731627) why do we have to do this manually? Check how is done in Device Explorer.
    val treeKeyAdapter = object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == KeyEvent.VK_ENTER) {
          fireAction(viewContext.schemaTree!!, event)
        }
      }
    }

    val treeDoubleClickListener = object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        fireAction(viewContext.schemaTree!!, event)
        return true
      }
    }

    viewContext.schemaTree?.let {
      it.addKeyListener(treeKeyAdapter)
      treeDoubleClickListener.installOn(it)
    }
  }

  override fun addListener(listener: SqliteViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteViewListener) {
    listeners.remove(listener)
  }

  override fun startLoading(text: String) {
    // TODO(b/133320900) Should show proper loading UI.
    //  This loading logic is not the best now that multiple databases can be opened.
    //  This method is called each time a new database is opened.
    workBench.isVisible = true
    defaultUiPanel.rootPanel.isVisible = false
    workBench.setLoadingText(text)
  }

  override fun stopLoading() {
  }

  override fun addDatabaseSchema(database: SqliteDatabase, schema: SqliteSchema, index: Int) {
    val tree = viewContext.schemaTree!!
    val treeModel = tree.model as DefaultTreeModel
    val root = treeModel.root as DefaultMutableTreeNode

    val schemaNode = DefaultMutableTreeNode(database)
    schema.tables.forEach { table ->
      val tableNode = DefaultMutableTreeNode(table)
      table.columns.forEach { column -> tableNode.add(DefaultMutableTreeNode(column)) }
      schemaNode.add(tableNode)
    }

    treeModel.insertNodeInto(schemaNode, root, index)
    tree.expandPath(TreePath(schemaNode.path))
  }

  override fun updateDatabase(database: SqliteDatabase, toRemove: List<SqliteTable>, toAdd: List<IndexedSqliteTable>) {
    val treeModel = viewContext.schemaTree!!.model as DefaultTreeModel
    val currentSchemaNode = findTreeNode(database)
    currentSchemaNode.userObject = database

    currentSchemaNode.children().toList().map { it as DefaultMutableTreeNode }.forEach {
      if (toRemove.contains(it.userObject))
        treeModel.removeNodeFromParent(it)
    }

    toAdd.forEach {
      val newTableNode = DefaultMutableTreeNode(it.sqliteTable)
      it.sqliteTable.columns.forEach { column -> newTableNode.add(DefaultMutableTreeNode(column)) }
      treeModel.insertNodeInto(newTableNode, currentSchemaNode, it.index)
    }
  }

  override fun removeDatabaseSchema(database: SqliteDatabase) {
    val treeModel = viewContext.schemaTree!!.model as DefaultTreeModel
    val databaseNode = findTreeNode(database)
    treeModel.removeNodeFromParent(databaseNode)
  }

  override fun displayResultSet(tableId: TabId, tableName: String, component: JComponent) {
    val tab = createSqliteExplorerTab(tableId, tableName, component)
    tabs.addTab(tab)
    tabs.select(tab, true)
    openTabs[tableId] = tab
  }

  override fun focusTab(tabId: TabId) {
    tabs.select(openTabs[tabId]!!, true)
  }

  override fun closeTab(tabId: TabId) {
    val tab = openTabs.remove(tabId)
    tabs.removeTab(tab)
  }

  override fun reportErrorRelatedToService(service: SqliteService, message: String, t: Throwable) {
    val errorMessage = if (t.message != null) "$message: ${t.message}" else message
    workBench.loadingStopped(errorMessage)
  }

  override fun reportSyncProgress(message: String) {
    viewContext.syncLabel?.text = message
  }

  private fun createSqliteExplorerTab(tableId: TabId, tableName: String, tabContent: JComponent): TabInfo {
    val tab = TabInfo(tabContent)

    val tabActionGroup = DefaultActionGroup()
    tabActionGroup.add(object : AnAction("Close tabs", "Click to close tab", AllIcons.Actions.Close) {
      override fun actionPerformed(e: AnActionEvent) {
        listeners.forEach { it.closeTabActionInvoked(tableId) }
      }

      override fun update(e: AnActionEvent) {
        e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
        e.presentation.isVisible = true
        e.presentation.text = UIBundle.message("tabbed.pane.close.tab.action.name")
      }
    })
    tab.setTabLabelActions(tabActionGroup, ActionPlaces.EDITOR_TAB)
    tab.icon = AllIcons.Nodes.DataTables
    tab.text = tableName
    return tab
  }

  private fun findTreeNode(database: SqliteDatabase): DefaultMutableTreeNode {
    val root = viewContext.schemaTree!!.model.root as DefaultMutableTreeNode
    return root.children().asSequence()
      .map { it as DefaultMutableTreeNode }
      .first { it.userObject == database }
  }

  private fun fireAction(tree: Tree, e: InputEvent) {
    val lastPathComponent = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return

    val sqliteTable = lastPathComponent.userObject
    if (sqliteTable is SqliteTable) {
      val parentNode = lastPathComponent.parent as DefaultMutableTreeNode
      val database = parentNode.userObject as SqliteDatabase
      listeners.forEach { l -> l.tableNodeActionInvoked(database, sqliteTable) }
      e.consume()
    } else {
      val path = TreePath(lastPathComponent.path)
      if (tree.isExpanded(path)) {
        tree.collapsePath(path)
      } else {
        tree.expandPath(path)
      }
    }
  }

  private fun createToolWindowDefinition(): ToolWindowDefinition<SqliteViewContext> {
    return ToolWindowDefinition(
      "Open Databases",
      AllIcons.Nodes.DataTables,
      "OPEN_DATABASES",
      Side.LEFT,
      Split.TOP,
      AutoHide.DOCKED
    ) { SchemaPanelToolContent() }
  }

  private inner class TabMouseListener : MouseAdapter() {
    override fun mouseReleased(e: MouseEvent) {
      if(e.button == 2) {
        // TODO (b/135525331)
        // mouse wheel click
        //tabs.removeTab()
      }
    }
  }

  inner class SchemaPanelToolContent : ToolContent<SqliteViewContext> {

    private val schemaPanel = SqliteSchemaPanel()
    private val tree = schemaPanel.tree
    private val syncProgressLabel = JLabel()

    init {
      val closeDatabaseButton = CommonButton("Close db", AllIcons.Actions.Close)
      closeDatabaseButton.toolTipText = "Close db"
      schemaPanel.controlsPanel.add(closeDatabaseButton)

      closeDatabaseButton.addActionListener {
        val databaseToRemove = tree?.selectionPaths?.mapNotNull { findDatabaseNode(it) }
        listeners.forEach { databaseToRemove?.forEach { database -> it.removeDatabaseActionInvoked(database) } }
      }

      val syncButton = CommonButton("Sync", AllIcons.Actions.SynchronizeFS)
      syncButton.toolTipText = "Sync"
      schemaPanel.controlsPanel.add(syncButton)

      syncButton.addActionListener {
        val databaseToSync = tree?.selectionPaths?.mapNotNull { findDatabaseNode(it) }?.first() ?: return@addActionListener
        listeners.forEach { it.syncDatabaseActionInvoked(databaseToSync) }
      }

      schemaPanel.controlsPanel.add(syncProgressLabel)

      setUpSchemaTree(tree)
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

    private fun setUpSchemaTree(tree: Tree) {
      tree.cellRenderer = SchemaTreeCellRenderer()
      val root = DefaultMutableTreeNode("Schemas")

      tree.model = DefaultTreeModel(root)
      tree.toggleClickCount = 0
    }

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
      toolContext?.syncLabel = syncProgressLabel
    }
  }

  class SqliteViewContext {
    var schemaTree: Tree? = null
    var syncLabel: JLabel? = null
  }
}