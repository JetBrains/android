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
import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.sqlite.controllers.TabId
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.logtab.LogTabView
import com.android.tools.idea.sqlite.ui.logtab.LogTabViewImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.UIBundle
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.UiDecorator
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

@UiThread
class DatabaseInspectorViewImpl(
  project: Project,
  parentDisposable: Disposable
) : DatabaseInspectorView {
  val listeners = mutableListOf<DatabaseInspectorView.Listener>()

  private val leftPanelView = LeftPanelView(this)
  private val viewContext = SqliteViewContext(leftPanelView.component)
  private val workBench: WorkBench<SqliteViewContext> = WorkBench(project, "Sqlite", null, parentDisposable)
  private val tabs = JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.getInstance(project), project)
  private val logTabView = LogTabViewImpl(project)

  override val component: JComponent = workBench

  private val openTabs = mutableMapOf<TabId, TabInfo>()

  init {
    val panel = JPanel(BorderLayout())
    val centerPanel = JPanel(BorderLayout())
    panel.add(centerPanel, BorderLayout.CENTER)

    workBench.init(panel, viewContext, listOf(createToolWindowDefinition()), false)

    tabs.apply {
      isTabDraggingEnabled = true
      setUiDecorator { UiDecorator.UiDecoration(null, JBUI.insets(4, 10)) }
      addTabMouseListener(TabMouseListener())
    }

    centerPanel.add(tabs)

    setUpLogTab()
  }

  private fun setUpLogTab() {
    val tab = TabInfo(logTabView.component)
    tab.text = "Log"

    tabs.addTab(tab)
    tabs.select(tab, true)
  }

  override fun addListener(listener: DatabaseInspectorView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: DatabaseInspectorView.Listener) {
    listeners.remove(listener)
  }

  override fun startLoading(text: String) {
    // TODO(b/133320900) Should show proper loading UI.
    //  This loading logic is not the best now that multiple databases can be opened.
    //  This method is called each time a new database is opened.
    workBench.setLoadingText(text)
  }

  override fun stopLoading() {
  }

  override fun addDatabaseSchema(database: SqliteDatabase, schema: SqliteSchema, index: Int) {
    leftPanelView.addDatabaseSchema(database, schema, index)
  }

  override fun updateDatabase(database: SqliteDatabase, toAdd: List<SqliteTable>) {
    leftPanelView.updateDatabase(database, toAdd)
  }

  override fun removeDatabaseSchema(database: SqliteDatabase) {
    leftPanelView.removeDatabaseSchema(database)
  }

  override fun openTab(tableId: TabId, tabName: String, component: JComponent) {
    val tab = createSqliteExplorerTab(tableId, tabName, component)
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

  override fun reportError(message: String, t: Throwable) {
    val errorMessage = if (t.message != null) "$message: ${t.message}" else message
    workBench.loadingStopped(errorMessage)
  }

  override fun getLogTabView(): LogTabView {
    return logTabView
  }

  override fun reportSyncProgress(message: String) {
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
    private lateinit var component: JComponent

    /**
     * Initialize the UI from the passed in [SqliteViewContext]
     */
    override fun setToolContext(toolContext: SqliteViewContext?) {
      component = toolContext?.component ?: JPanel()
    }

    override fun getComponent(): JComponent {
      return component
    }

    override fun dispose() {
    }
  }

  data class SqliteViewContext(val component: JComponent)
}