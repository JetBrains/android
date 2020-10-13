package com.android.tools.idea.appinspection.inspectors.workmanager.view

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.intellij.icons.AllIcons
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
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable

/**
 * Parent panel which contains toggleable different views into workers, e.g. a table view and a graph view.
 */
class WorksContentView(private val tab: WorkManagerInspectorTab,
                       private val client: WorkManagerInspectorClient) : JPanel() {
  private inner class CancelAction :
    AnAction(WorkManagerInspectorBundle.message("action.cancel.work"), "", AllIcons.Actions.Suspend) {

    override fun actionPerformed(e: AnActionEvent) {
      val id = tab.selectedWork?.id ?: return
      client.cancelWorkById(id)
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

  private val contentScrollPane: JScrollPane
  private val tableView: JTable

  init {
    layout = TabularLayout("*", "Fit,*")
    add(buildActionBar(), TabularLayout.Constraint(0, 0))

    contentScrollPane = JBScrollPane()
    tableView = WorksTableView(tab, client)
    contentScrollPane.setViewportView(tableView)
    add(contentScrollPane, TabularLayout.Constraint(1, 0))
  }

  private fun buildActionBar(): JComponent {
    val group = DefaultActionGroup().apply {
      add(CancelAction())
      addSeparator()
      add(TagsDropDownAction())
      addSeparator()
    }
    val toolbar = ActionManager.getInstance().createActionToolbar("WorkManagerInspector", group, true)
    return toolbar.component
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
