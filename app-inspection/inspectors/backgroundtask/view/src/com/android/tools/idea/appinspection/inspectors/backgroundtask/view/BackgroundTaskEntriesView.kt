/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBViewport
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

const val WORK_MANAGER_TOOLBAR_PLACE = "WorkManagerInspector"

/**
 * View containing a table view and graph view, and offers toggle control between the two.
 */
class BackgroundTaskEntriesView(client: BackgroundTaskInspectorClient,
                                private val selectionModel: EntrySelectionModel) : JPanel() {
  enum class Mode {
    TABLE,
    GRAPH
  }

  private inner class TableViewAction :
    AnAction(BackgroundTaskInspectorBundle.message("action.show.list"), "", AllIcons.Graph.Grid) {

    override fun actionPerformed(e: AnActionEvent) {
      if (contentMode == Mode.GRAPH) {
        contentMode = Mode.TABLE
        tableView.component.requestFocusInWindow()
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = contentMode == Mode.GRAPH
    }
  }

  private inner class GraphViewAction :
    AnAction(BackgroundTaskInspectorBundle.message("action.show.graph"), "", AllIcons.Graph.Layout) {

    override fun actionPerformed(e: AnActionEvent) {
      val selectedWork = selectionModel.selectedEntry
      if (contentMode == Mode.TABLE && selectedWork != null) {
        contentMode = Mode.GRAPH
        graphView.requestFocusInWindow()
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = contentMode == Mode.TABLE && selectionModel.selectedEntry is WorkEntry
    }
  }

  var contentMode = Mode.TABLE
    set(value) {
      if (field != value) {
        field = value
        ActivityTracker.getInstance().inc()
        when (value) {
          Mode.TABLE -> {
            contentScrollPane.setViewportView(getContentView())
          }
          Mode.GRAPH -> {
            contentScrollPane.setViewportView(getContentView())
          }
        }
        contentScrollPane.revalidate()
      }
    }

  private val contentScrollPane: JScrollPane
  private val tableView: BackgroundTaskTreeTableView
  private val graphView: WorkDependencyGraphView

  init {
    layout = TabularLayout("*", "Fit,*")
    add(buildActionBar(), TabularLayout.Constraint(0, 0))

    contentScrollPane = JBScrollPane()
    // Remove redundant borders from left, right and bottom.
    contentScrollPane.border = AdtUiUtils.DEFAULT_TOP_BORDER
    contentScrollPane.horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
    tableView = BackgroundTaskTreeTableView(client, selectionModel)
    graphView = WorkDependencyGraphView(client, selectionModel)
    contentScrollPane.setViewportView(tableView.component)
    add(contentScrollPane, TabularLayout.Constraint(1, 0))

    selectionModel.registerWorkSelectionListener { entry ->
      if (entry == null) {
        contentMode = Mode.TABLE
      }
    }
  }

  private fun buildActionBar(): JComponent {
    val toolbarPanel = JPanel(BorderLayout())

    val rightGroup = DefaultActionGroup().apply {
      add(TableViewAction())
      add(GraphViewAction())
    }
    val rightToolbar = ActionManager.getInstance().createActionToolbar(WORK_MANAGER_TOOLBAR_PLACE, rightGroup, true)
    ActionToolbarUtil.makeToolbarNavigable(rightToolbar)
    toolbarPanel.add(rightToolbar.component, BorderLayout.EAST)

    return toolbarPanel
  }

  private fun getContentView(): JComponent = when (contentMode) {
    Mode.TABLE -> tableView.component
    Mode.GRAPH -> graphView
  }
}

fun JComponent.scrollToCenter() {
  var component: JComponent = this
  val point = Point(bounds.width / 2, bounds.height / 2)
  while (component.parent !is JBViewport) {
    point.x += component.bounds.x
    point.y += component.bounds.y
    component = component.parent as? JComponent ?: return
  }
  val viewport = component.parent as JBViewport
  point.x -= viewport.size.width / 2
  point.y -= viewport.size.height / 2
  val viewSize = viewport.viewSize

  if (viewSize.width >= viewport.size.width) {
    point.x = point.x.coerceAtLeast(0).coerceAtMost(viewSize.width - viewport.size.width)
  }
  else {
    point.x = 0
  }
  if (viewSize.height >= viewport.size.height) {
    point.y = point.y.coerceAtLeast(0).coerceAtMost(viewSize.height - viewport.size.height)
  }
  else {
    point.y = 0
  }
  viewport.viewPosition = point
}
