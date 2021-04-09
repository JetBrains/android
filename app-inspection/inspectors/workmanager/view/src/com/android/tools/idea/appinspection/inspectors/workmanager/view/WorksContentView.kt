package com.android.tools.idea.appinspection.inspectors.workmanager.view

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.inspectors.workmanager.analytics.toChainInfo
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkSelectionModel
import com.android.tools.idea.concurrency.AndroidDispatchers
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
import com.intellij.ui.components.JBViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable

const val WORK_MANAGER_TOOLBAR_PLACE = "WorkManagerInspector"

/**
 * Parent panel which contains toggleable different views into workers, e.g. a table view and a graph view.
 */
class WorksContentView(private val tab: WorkManagerInspectorTab,
                       private val workSelectionModel: WorkSelectionModel,
                       private val client: WorkManagerInspectorClient) : JPanel() {
  enum class Mode {
    TABLE,
    GRAPH
  }

  private inner class CancelAction :
    AnAction(WorkManagerInspectorBundle.message("action.cancel.work"), "", AllIcons.Actions.Suspend) {

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = workSelectionModel.selectedWork?.state?.isFinished() == false
    }

    override fun actionPerformed(e: AnActionEvent) {
      val id = workSelectionModel.selectedWork?.id ?: return
      client.cancelWorkById(id)
      client.tracker.trackWorkCancelled()
      tableView.requestFocusInWindow()
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
        workSelectionModel.setSelectedWork(workSelectionModel.selectedWork, WorkSelectionModel.Context.TOOLBAR)
        tableView.requestFocusInWindow()
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
      val selectedWork = workSelectionModel.selectedWork
      if (contentMode == Mode.TABLE && selectedWork != null) {
        contentMode = Mode.GRAPH
        workSelectionModel.setSelectedWork(workSelectionModel.selectedWork, WorkSelectionModel.Context.TOOLBAR)
        graphView.requestFocusInWindow()
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = contentMode == Mode.TABLE && workSelectionModel.selectedWork != null
    }
  }

  var contentMode = Mode.TABLE
    set(value) {
      if (field != value) {
        field = value
        ActivityTracker.getInstance().inc()
        when (value) {
          Mode.TABLE -> {
            contentScrollPane.setViewportView(buildContentViewportView())
            client.tracker.trackTableModeSelected()
          }
          Mode.GRAPH -> {
            client.filterTag = null
            contentScrollPane.setViewportView(buildContentViewportView())
            client.tracker.trackGraphModeSelected(AppInspectionEvent.WorkManagerInspectorEvent.Context.TOOL_BUTTON_CONTEXT,
                                                  client.getOrderedWorkChain(workSelectionModel.selectedWork!!.id).toChainInfo())
          }
        }
        contentScrollPane.revalidate()
      }
    }

  private val contentScrollPane: JScrollPane
  private val tableView: JTable
  private val graphView: WorkDependencyGraphView

  init {
    layout = TabularLayout("*", "Fit,*")
    add(buildActionBar(), TabularLayout.Constraint(0, 0))

    contentScrollPane = JBScrollPane()
    tableView = WorksTableView(tab, client, workSelectionModel)
    graphView = WorkDependencyGraphView(tab, client, workSelectionModel) {
      contentMode = Mode.TABLE
      contentScrollPane.setViewportView(tableView)
    }
    contentScrollPane.setViewportView(tableView)
    add(contentScrollPane, TabularLayout.Constraint(1, 0))

    // Handle data changes from client.
    client.addWorksChangedListener {
      CoroutineScope(AndroidDispatchers.uiThread).launch {
        if (workSelectionModel.selectedWork != null) {
          val work = client.lockedWorks { works ->
            works.firstOrNull { it.id == workSelectionModel.selectedWork?.id }
          }
          if (work != null) {
            // Update existing work changes e.g. State changes from Running to Succeed
            workSelectionModel.setSelectedWork(work, WorkSelectionModel.Context.DEVICE)
          }
          else {
            // Select the first row from the table when the selected work is removed when Table content mode is enabled.
            if (contentMode == Mode.TABLE && tableView.rowCount > 0) {
              workSelectionModel.setSelectedWork(client.lockedWorks { works -> works.getOrNull(tableView.convertRowIndexToModel(0)) },
                                                 WorkSelectionModel.Context.DEVICE)
            }
            // Close the details view otherwise
            else {
              tab.isDetailsViewVisible = false
              workSelectionModel.setSelectedWork(null, WorkSelectionModel.Context.DEVICE)
            }
          }
        }
      }
    }
  }

  private fun buildActionBar(): JComponent {
    val toolbarPanel = JPanel(BorderLayout())

    val leftGroup = DefaultActionGroup().apply {
      add(CancelAction())
      addSeparator()
      add(TagsDropDownAction())
    }
    val leftToolbar = ActionManager.getInstance().createActionToolbar(WORK_MANAGER_TOOLBAR_PLACE, leftGroup, true)
    ActionToolbarUtil.makeToolbarNavigable(leftToolbar)
    toolbarPanel.add(leftToolbar.component, BorderLayout.WEST)

    if (ENABLE_WORK_MANAGER_GRAPH_VIEW.get()) {
      val rightGroup = DefaultActionGroup().apply {
        add(ListViewAction())
        add(GraphViewAction())
      }
      val rightToolbar = ActionManager.getInstance().createActionToolbar(WORK_MANAGER_TOOLBAR_PLACE, rightGroup, true)
      ActionToolbarUtil.makeToolbarNavigable(rightToolbar)
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
