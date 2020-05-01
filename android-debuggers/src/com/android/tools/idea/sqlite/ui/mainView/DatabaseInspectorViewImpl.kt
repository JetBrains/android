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
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.ui.notifyError
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.UIBundle
import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.UiDecorator
import com.intellij.ui.tabs.impl.JBEditorTabsBorder
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.text.html.HTMLDocument

@UiThread
class DatabaseInspectorViewImpl(
  project: Project,
  parentDisposable: Disposable
) : DatabaseInspectorView {
  val listeners = mutableListOf<DatabaseInspectorView.Listener>()

  private val centerPanel = JPanel(BorderLayout())
  private val leftPanelView = LeftPanelView(this)
  private val viewContext = SqliteViewContext(leftPanelView.component)
  private val workBench: WorkBench<SqliteViewContext> = WorkBench(project, "Database Inspector", null, parentDisposable)
  private val tabs = BorderedTabs(project, IdeFocusManager.getInstance(project), project)

  override val component: JComponent = workBench

  private val openTabs = mutableMapOf<TabId, TabInfo>()
  private val defaultEmptyStateMessage = "Open a table or run a query to begin inspecting your app's databases."

  init {
    workBench.init(centerPanel, viewContext, listOf(createToolWindowDefinition()), false)

    addEmptyStatePanel("Waiting for the app to open a connection to the database...")

    tabs.name = "right-panel-tabs-panel"
    tabs.apply {
      isTabDraggingEnabled = true
      setUiDecorator { UiDecorator.UiDecoration(null, JBUI.insets(5, 10, 5, 10)) }
      addTabMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (UIUtil.isCloseClick(e)) {
            val targetTabInfo = findInfo(e)
            val tabId = targetTabInfo?.`object` as? TabId ?: return
            listeners.forEach { it.closeTabActionInvoked(tabId) }
          }
        }
      })
    }
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

  override fun updateDatabases(databaseDiffOperations: List<DatabaseDiffOperation>) {
    for (databaseDiffOperation in databaseDiffOperations) {
      when (databaseDiffOperation) {
        is DatabaseDiffOperation.AddDatabase -> {
          leftPanelView.addDatabaseSchema(databaseDiffOperation.databaseId, databaseDiffOperation.schema, databaseDiffOperation.index)
        }
        is DatabaseDiffOperation.RemoveDatabase -> leftPanelView.removeDatabaseSchema(databaseDiffOperation.databaseId)
      }
    }

    if (openTabs.isEmpty() || leftPanelView.databasesCount == 0) {
      addEmptyStatePanel(defaultEmptyStateMessage)
    }
  }

  override fun updateDatabaseSchema(databaseId: SqliteDatabaseId, diffOperations: List<SchemaDiffOperation>) {
    leftPanelView.updateDatabase(databaseId, diffOperations)
  }

  override fun openTab(tabId: TabId, tabName: String, component: JComponent) {
    if (openTabs.isEmpty()) {
      centerPanel.removeAll()
      centerPanel.layout = BorderLayout()
      centerPanel.add(tabs, BorderLayout.CENTER)
      centerPanel.revalidate()
      centerPanel.repaint()
    }

    val tab = createTab(tabId, tabName, component)
    tabs.addTab(tab)
    tabs.select(tab, true)
    openTabs[tabId] = tab
  }

  override fun focusTab(tabId: TabId) {
    tabs.select(openTabs[tabId]!!, true)
  }

  override fun closeTab(tabId: TabId) {
    val tab = openTabs.remove(tabId)
    tabs.removeTab(tab)

    if (openTabs.isEmpty()) {
      addEmptyStatePanel(defaultEmptyStateMessage)
    }
  }

  override fun reportError(message: String, throwable: Throwable?) {
    notifyError(message, throwable)
  }

  override fun reportSyncProgress(message: String) {
  }

  private fun addEmptyStatePanel(text: String) {
    val escapedText = text.replace("&", "&amp").replace("<", "&lt").replace(">", "&gt")
    val editorPane = JEditorPane(
      "text/html",
      "<h2>Database Inspector</h2>" +
       escapedText +
      "<p><a href=\"https://d.android.com/r/studio-ui/db-inspector-help\">Learn more</a></p>"
    )
    val document = editorPane.document as HTMLDocument
    document.styleSheet.addRule(
      "body { text-align: center; font-family: ${UIUtil.getLabelFont()}; font-size: ${UIUtil.getLabelFont().size} pt; }"
    )
    document.styleSheet.addRule("h2 { font-weight: normal; }")
    editorPane.name = "right-panel-empty-state"
    editorPane.isOpaque = false
    editorPane.isEditable = false
    editorPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)

    centerPanel.removeAll()
    centerPanel.layout = GridBagLayout()
    centerPanel.add(editorPane)
    centerPanel.revalidate()
    centerPanel.repaint()
  }

  private fun createTab(tabId: TabId, tableName: String, tabContent: JComponent): TabInfo {
    val tab = TabInfo(tabContent)
    tab.`object` = tabId

    val tabActionGroup = DefaultActionGroup()
    tabActionGroup.add(object : AnAction("Close tabs", "Click to close tab", AllIcons.Actions.Close) {
      override fun actionPerformed(e: AnActionEvent) {
        listeners.forEach { it.closeTabActionInvoked(tabId) }
      }

      override fun update(e: AnActionEvent) {
        e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
        e.presentation.isVisible = true
        e.presentation.text = UIBundle.message("tabbed.pane.close.tab.action.name")
      }
    })
    tab.setTabLabelActions(tabActionGroup, ActionPlaces.EDITOR_TAB)
    tab.icon = StudioIcons.DatabaseInspector.TABLE
    tab.text = tableName
    return tab
  }

  private fun createToolWindowDefinition(): ToolWindowDefinition<SqliteViewContext> {
    return ToolWindowDefinition(
      "Databases",
      StudioIcons.DatabaseInspector.TABLE,
      "DATABASES",
      Side.LEFT,
      Split.TOP,
      AutoHide.DOCKED,
      ToolWindowDefinition.DEFAULT_SIDE_WIDTH,
      ToolWindowDefinition.DEFAULT_BUTTON_SIZE,
      ToolWindowDefinition.ALLOW_BASICS
    ) { SchemaPanelToolContent() }
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

  /**
   * Extends [JBTabsImpl] by using a [JBEditorTabsBorder], which adds a tab border to all the tabs.
   * The [JBTabsBorder] used by [JBTabsImpl] does not add a border to the first tab, if there is only one tab.
   */
  private class BorderedTabs(
    project: Project,
    focusManager: IdeFocusManager,
    parent: Disposable
  ) : JBTabsImpl(project, focusManager, parent) {
    override fun createTabBorder() = JBEditorTabsBorder(this)
  }
}