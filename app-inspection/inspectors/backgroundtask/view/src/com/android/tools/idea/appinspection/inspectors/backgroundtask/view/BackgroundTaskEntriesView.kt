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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.toChainInfo
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.table.BackgroundTaskTreeTableView
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBViewport
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Point
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

const val WORK_MANAGER_TOOLBAR_PLACE = "WorkManagerInspector"
private const val MINIMUM_ENTRIES_VIEW_WIDTH = 400

/** View containing a table view and graph view, and offers toggle control between the two. */
class BackgroundTaskEntriesView(
  tab: BackgroundTaskInspectorTab,
  private val client: BackgroundTaskInspectorClient,
  private val selectionModel: EntrySelectionModel,
  scope: CoroutineScope,
  uiDispatcher: CoroutineDispatcher
) : JPanel() {
  enum class Mode {
    TABLE,
    GRAPH
  }

  private inner class CancelAction :
    AnAction(
      BackgroundTaskInspectorBundle.message("action.cancel.work"),
      "",
      AllIcons.Actions.Suspend
    ) {

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = selectionModel.selectedWork?.state?.isFinished() == false
    }

    override fun actionPerformed(e: AnActionEvent) {
      val id = selectionModel.selectedWork?.id ?: return
      client.cancelWorkById(id)
      client.tracker.trackWorkCancelled()
      if (contentMode == Mode.TABLE) {
        tableView.component.requestFocusInWindow()
      } else {
        graphView.requestFocusInWindow()
      }
    }
  }

  /** DropDownAction that shows tags from available works. */
  private inner class TagsDropDownAction :
    DropDownAction(
      BackgroundTaskInspectorBundle.message("action.tag.all"),
      BackgroundTaskInspectorBundle.message("action.tag.tooltip"),
      null
    ) {
    private var selectedTag: String? = null

    override fun update(event: AnActionEvent) {
      if (selectedTag != tableView.treeModel.filterTag) {
        selectedTag = tableView.treeModel.filterTag
        event.presentation.text =
          selectedTag ?: BackgroundTaskInspectorBundle.message("action.tag.all")
      }
      val isTableActive = (contentMode == Mode.TABLE)
      if (event.presentation.isVisible != isTableActive) {
        event.presentation.isVisible = isTableActive
      }
    }

    override fun canBePerformed(context: DataContext): Boolean {
      return tableView.treeModel.allTags.isNotEmpty()
    }

    public override fun updateActions(context: DataContext): Boolean {
      removeAll()
      add(FilterWithTagToggleAction(null))
      tableView.treeModel.allTags.forEach { tag -> add(FilterWithTagToggleAction(tag)) }
      return true
    }

    override fun displayTextInToolbar() = true
  }

  /** ToggleAction that filters works with a specific [tag]. */
  private inner class FilterWithTagToggleAction(private val tag: String?) :
    ToggleAction(tag ?: "All tags") {
    override fun isSelected(event: AnActionEvent): Boolean {
      return tag == tableView.treeModel.filterTag
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      tableView.treeModel.filterTag = tag
    }
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
    AnAction(
      BackgroundTaskInspectorBundle.message("action.show.graph"),
      "",
      AllIcons.Graph.Layout
    ) {

    override fun actionPerformed(e: AnActionEvent) {
      val selectedWork = selectionModel.selectedEntry
      if (contentMode == Mode.TABLE && selectedWork != null) {
        contentMode = Mode.GRAPH
        graphView.requestFocusInWindow()
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled =
        contentMode == Mode.TABLE && selectionModel.selectedEntry is WorkEntry
    }
  }

  var contentMode = Mode.TABLE
    set(value) {
      if (field != value) {
        field = value
        listeners.forEach { listener -> listener(value) }
      }
    }

  private var listeners = mutableListOf<(Mode) -> Unit>()
  private val cardLayout: CardLayout
  private val contentPanel: JPanel

  @VisibleForTesting val tableView: BackgroundTaskTreeTableView

  @VisibleForTesting val graphView: WorkDependencyGraphView

  init {
    tableView = BackgroundTaskTreeTableView(tab, client, selectionModel, scope, uiDispatcher)
    graphView = WorkDependencyGraphView(tab, client, selectionModel, scope, uiDispatcher)

    layout = TabularLayout("*", "Fit,*")
    minimumSize = Dimension(MINIMUM_ENTRIES_VIEW_WIDTH, minimumSize.height)
    add(buildActionBar(), TabularLayout.Constraint(0, 0))

    cardLayout = CardLayout()
    contentPanel = JPanel(cardLayout)
    // Remove redundant borders from left, right and bottom.
    contentPanel.border = AdtUiUtils.DEFAULT_TOP_BORDER
    contentPanel.add(tableView.component, Mode.TABLE.name)
    val scrollPane =
      JBScrollPane(graphView).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = BorderFactory.createEmptyBorder()
      }
    contentPanel.add(scrollPane, Mode.GRAPH.name)
    add(contentPanel, TabularLayout.Constraint(1, 0))

    selectionModel.registerEntrySelectionListener { entry ->
      if (entry == null) {
        contentMode = Mode.TABLE
      }
    }

    addContentModeChangedListener {
      ActivityTracker.getInstance().inc()
      cardLayout.show(contentPanel, contentMode.name)
      when (contentMode) {
        Mode.TABLE -> {
          client.tracker.trackTableModeSelected()
        }
        Mode.GRAPH -> {
          client.tracker.trackGraphModeSelected(
            AppInspectionEvent.BackgroundTaskInspectorEvent.Context.TOOL_BUTTON_CONTEXT,
            client.getOrderedWorkChain(selectionModel.selectedWork!!.id).toChainInfo()
          )
        }
      }
      contentPanel.revalidate()
    }
  }

  fun addContentModeChangedListener(listener: (Mode) -> Unit) {
    listener(contentMode)
    listeners.add(listener)
  }

  private fun buildActionBar(): JComponent {
    val toolbarPanel = JPanel(BorderLayout())
    val leftGroup =
      DefaultActionGroup().apply {
        add(CancelAction())
        addSeparator()
        add(TagsDropDownAction())
      }
    val leftToolbar =
      ActionManager.getInstance().createActionToolbar(WORK_MANAGER_TOOLBAR_PLACE, leftGroup, true)
    leftToolbar.setTargetComponent(this)
    ActionToolbarUtil.makeToolbarNavigable(leftToolbar)
    toolbarPanel.add(leftToolbar.component, BorderLayout.WEST)

    val rightGroup =
      DefaultActionGroup().apply {
        add(TableViewAction())
        add(GraphViewAction())
      }
    val rightToolbar =
      ActionManager.getInstance().createActionToolbar(WORK_MANAGER_TOOLBAR_PLACE, rightGroup, true)
    rightToolbar.setTargetComponent(this)
    ActionToolbarUtil.makeToolbarNavigable(rightToolbar)
    toolbarPanel.add(rightToolbar.component, BorderLayout.EAST)

    return toolbarPanel
  }

  /** @return a list of actions from the drop down menu that filter works with a tag. */
  @TestOnly
  fun getFilterActionList(): List<ToggleAction> {
    val toolbar =
      TreeWalker(this).descendantStream().filter { it is ActionToolbar }.findFirst().get()
        as ActionToolbarImpl
    val selectFilterAction = toolbar.actions[2] as TagsDropDownAction
    selectFilterAction.updateActions(DataContext.EMPTY_CONTEXT)
    return selectFilterAction.getChildren(null).map { it as ToggleAction }
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
  } else {
    point.x = 0
  }
  if (viewSize.height >= viewport.size.height) {
    point.y = point.y.coerceAtLeast(0).coerceAtMost(viewSize.height - viewport.size.height)
  } else {
    point.y = 0
  }
  viewport.viewPosition = point
}
