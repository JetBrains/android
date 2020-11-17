package com.android.tools.idea.appinspection.inspectors.workmanager.view

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.appinspection.inspectors.workmanager.analytics.toChainInfo
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.flags.StudioFlags.ENABLE_WORK_MANAGER_GRAPH_VIEW
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
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable

const val WORK_MANAGER_TOOLBAR_PLACE = "WorkManagerInspector"

/**
 * Parent panel which contains toggleable different views into workers, e.g. a table view and a graph view.
 */
class WorksContentView(private val tab: WorkManagerInspectorTab,
                       private val client: WorkManagerInspectorClient) : JPanel() {
  private enum class Mode {
    TABLE,
    GRAPH
  }

  private inner class CancelAction :
    AnAction(WorkManagerInspectorBundle.message("action.cancel.work"), "", AllIcons.Actions.Suspend) {

    override fun actionPerformed(e: AnActionEvent) {
      val id = tab.selectedWork?.id ?: return
      client.cancelWorkById(id)
      client.tracker.trackWorkCancelled()
    }
  }

  /**
   * DropDownAction that shows tags from available works.
   */
  private inner class TagsDropDownAction :
    DropDownAction(WorkManagerInspectorBundle.message("action.tag.all"),
                   WorkManagerInspectorBundle.message("action.tag.tooltip"),
                   null) {
    private var selectedTag: String? = null

    override fun update(event: AnActionEvent) {
      if (selectedTag != client.filterTag) {
        selectedTag = client.filterTag
        event.presentation.text = selectedTag ?: WorkManagerInspectorBundle.message("action.tag.all")
      }
      val isTableActive = (contentMode == Mode.TABLE)
      if (event.presentation.isVisible != isTableActive) {
        event.presentation.isVisible = isTableActive
      }
    }

    public override fun updateActions(context: DataContext): Boolean {
      removeAll()
      add(FilterWithTagToggleAction(null))
      client.getAllTags().forEach { tag ->
        add(FilterWithTagToggleAction(tag))
      }
      return true
    }

    override fun displayTextInToolbar() = true
  }

  /**
   * ToggleAction that filters works with a specific [tag].
   */
  private inner class FilterWithTagToggleAction(private val tag: String?)
    : ToggleAction(tag ?: "All tags") {
    override fun isSelected(event: AnActionEvent): Boolean {
      return tag == client.filterTag
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      client.filterTag = tag
    }
  }

  private inner class ListViewAction :
    AnAction(WorkManagerInspectorBundle.message("action.show.list"), "", AllIcons.Graph.Grid) {

    override fun actionPerformed(e: AnActionEvent) {
      if (contentMode == Mode.GRAPH) {
        contentMode = Mode.TABLE
        client.tracker.trackTableModeSelected()
        contentScrollPane.setViewportView(buildContentViewportView())
        contentScrollPane.revalidate()
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = contentMode == Mode.GRAPH
    }
  }

  private inner class GraphViewAction :
    AnAction(WorkManagerInspectorBundle.message("action.show.graph"), "", AllIcons.Graph.Layout) {

    override fun actionPerformed(e: AnActionEvent) {
      val selectedWork = tab.selectedWork
      if (contentMode == Mode.TABLE && selectedWork != null) {
        contentMode = Mode.GRAPH
        client.tracker.trackGraphModeSelected(AppInspectionEvent.WorkManagerInspectorEvent.Context.TOOL_BUTTON_CONTEXT,
                                              client.getOrderedWorkChain(selectedWork.id).toChainInfo())
        client.filterTag = null
        contentScrollPane.setViewportView(buildContentViewportView())
        contentScrollPane.revalidate()
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = contentMode == Mode.TABLE && tab.selectedWork != null
    }
  }

  private var contentMode = Mode.TABLE
    set(value) {
      if (field != value) {
        field = value
        ActivityTracker.getInstance().inc()
      }
    }

  private val contentScrollPane: JScrollPane
  private val tableView: JTable
  private val graphView: WorkDependencyGraphView

  init {
    layout = TabularLayout("*", "Fit,*")
    add(buildActionBar(), TabularLayout.Constraint(0, 0))

    contentScrollPane = JBScrollPane()
    tableView = WorksTableView(tab, client)
    graphView = WorkDependencyGraphView(client, tab) {
      contentMode = Mode.TABLE
      contentScrollPane.setViewportView(tableView)
    }
    contentScrollPane.setViewportView(tableView)
    add(contentScrollPane, TabularLayout.Constraint(1, 0))
  }

  private fun buildActionBar(): JComponent {
    val toolbarPanel = JPanel(BorderLayout())

    val leftGroup = DefaultActionGroup().apply {
      add(CancelAction())
      addSeparator()
      add(TagsDropDownAction())
    }
    val leftToolbar = ActionManager.getInstance().createActionToolbar(WORK_MANAGER_TOOLBAR_PLACE, leftGroup, true)
    toolbarPanel.add(leftToolbar.component, BorderLayout.WEST)

    if (ENABLE_WORK_MANAGER_GRAPH_VIEW.get()) {
      val rightGroup = DefaultActionGroup().apply {
        add(ListViewAction())
        add(GraphViewAction())
      }
      val rightToolbar = ActionManager.getInstance().createActionToolbar(WORK_MANAGER_TOOLBAR_PLACE, rightGroup, true)
      toolbarPanel.add(rightToolbar.component, BorderLayout.EAST)
    }

    return toolbarPanel
  }

  private fun buildContentViewportView(): JComponent = when (contentMode) {
    Mode.TABLE -> tableView
    Mode.GRAPH -> graphView
  }

  /**
   * @return a list of actions from the drop down menu that filter works with a tag.
   */
  @TestOnly
  fun getFilterActionList(): List<ToggleAction> {
    val toolbar = TreeWalker(this).descendantStream().filter { it is ActionToolbar }.findFirst().get() as ActionToolbarImpl
    val selectFilterAction = toolbar.actions[2] as TagsDropDownAction
    selectFilterAction.updateActions(DataContext.EMPTY_CONTEXT)
    return selectFilterAction.getChildren(null).map { it as ToggleAction }
  }
}
