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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBSplitter
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

private const val BUTTON_SIZE = 24 // Icon is 16x16. This gives it some padding, so it doesn't touch the border.
private val BUTTON_DIMENS = Dimension(JBUI.scale(BUTTON_SIZE), JBUI.scale(BUTTON_SIZE))

/**
 * View class for the WorkManger Inspector Tab with a table of all active works.
 */
class WorkManagerInspectorTab(private val client: WorkManagerInspectorClient,
                              ideServices: AppInspectionIdeServices,
                              scope: CoroutineScope) {

  private class WorksTableStateCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {

      val state = WorkInfo.State.forNumber(value as Int)
      super.getTableCellRendererComponent(table, state.capitalizedName(), isSelected, hasFocus, row, column)
      icon = state.icon()
      return this
    }
  }

  private class WorksTableTimeCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component =
      super.getTableCellRendererComponent(table, (value as Long).toFormattedTimeString(), isSelected, hasFocus, row, column)
  }

  private class WorksTableDataCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {
      val pair = value as Pair<*, *>
      val data = pair.second as WorkManagerInspectorProtocol.Data
      val text = if (data.entriesList.isEmpty()) {
        if ((pair.first as WorkInfo.State).isFinished()) {
          foreground = WorkManagerInspectorColors.DATA_TEXT_NULL_COLOR
          WorkManagerInspectorBundle.message("table.data.null")

        }
        else {
          foreground = WorkManagerInspectorColors.DATA_TEXT_AWAITING_COLOR
          WorkManagerInspectorBundle.message("table.data.awaiting")
        }
      }
      else {
        foreground = null
        data.entriesList.joinToString(prefix = "{ ", postfix = " }") { "${it.key}: ${it.value}" }
      }
      super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
      return this
    }
  }

  private inner class CancelAction :
    AnAction(WorkManagerInspectorBundle.message("action.cancel.work"), "", AllIcons.Actions.Suspend) {

    override fun actionPerformed(e: AnActionEvent) {
      val id = client.getWorkInfoOrNull(selectedModelRow)?.id
      if (id != null) {
        client.cancelWorkById(id)
      }
    }
  }

  /**
   * DropDownAction that shows tags from available works.
   */
  private inner class TagsDropDownAction :
    DropDownAction(WorkManagerInspectorBundle.message("action.tag.all"),
                   WorkManagerInspectorBundle.message("action.tag.tooltip"),
                   null) {
    private var lastTag: String? = null

    override fun update(event: AnActionEvent) {
      if (lastTag != client.filterTag) {
        event.presentation.text = client.filterTag
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

  private val classNameProvider = ClassNameProvider(ideServices, scope)
  private val enqueuedAtProvider = EnqueuedAtProvider(ideServices, scope)

  private val splitter = JBSplitter(false).apply {
    border = AdtUiUtils.DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = buildTablePanel()
    secondComponent = null
  }

  val component: JComponent = splitter

  private var selectedModelRow = -1

  private fun buildTablePanel(): JComponent {
    val panel = JPanel(TabularLayout("*", "Fit,*"))
    panel.add(buildActionBar(), TabularLayout.Constraint(0, 0))
    panel.add(JScrollPane(buildWorksTable()), TabularLayout.Constraint(1, 0))

    return panel
  }

  private fun buildActionBar(): Component {
    val group = DefaultActionGroup().apply {
      add(CancelAction())
      addSeparator()
      add(TagsDropDownAction())
    }
    val toolbar = ActionManager.getInstance().createActionToolbar("WorkManagerInspector", group, true)
    return toolbar.component
  }

  private fun buildWorksTable(): JBTable {
    val model = WorksTableModel(client)
    // TODO (b/167190682) highlight the hovered row
    val table = JBTable(model)

    table.autoCreateRowSorter = true

    table.columnModel.getColumn(WorksTableModel.Column.ORDER.ordinal).cellRenderer = DefaultTableCellRenderer()
    table.columnModel.getColumn(WorksTableModel.Column.CLASS_NAME.ordinal).cellRenderer = DefaultTableCellRenderer()
    table.columnModel.getColumn(WorksTableModel.Column.STATE.ordinal).cellRenderer = WorksTableStateCellRenderer()
    table.columnModel.getColumn(WorksTableModel.Column.TIME_STARTED.ordinal).cellRenderer = WorksTableTimeCellRenderer()
    table.columnModel.getColumn(WorksTableModel.Column.DATA.ordinal).cellRenderer = WorksTableDataCellRenderer()

    // Adjusts width for each column.
    table.addComponentListener(object : ComponentAdapter() {
      fun refreshColumnSizes() {
        for (column in WorksTableModel.Column.values()) {
          table.columnModel.getColumn(column.ordinal).preferredWidth = (table.width * column.widthPercentage).toInt()
        }
      }

      override fun componentShown(e: ComponentEvent?) {
        refreshColumnSizes()
      }

      override fun componentResized(e: ComponentEvent) {
        refreshColumnSizes()
      }
    })

    model.addTableModelListener {
      ApplicationManager.getApplication().invokeLater {
        if (splitter.secondComponent == null) {
          return@invokeLater
        }
        if (selectedModelRow != -1) {
          val tableRow = table.convertRowIndexToView(selectedModelRow)
          table.addRowSelectionInterval(tableRow, tableRow)
          splitter.secondComponent = buildDetailedPanel(table, client.getWorkInfoOrNull(selectedModelRow))
        }
        else {
          splitter.secondComponent = buildDetailedPanel(table, null)
        }
      }
    }

    table.selectionModel.addListSelectionListener {
      if (table.selectedRow != -1) {
        selectedModelRow = table.convertRowIndexToModel(table.selectedRow)
        splitter.secondComponent = buildDetailedPanel(table, client.getWorkInfoOrNull(selectedModelRow))
      }
    }
    return table
  }

  private fun buildDetailedPanel(table: JTable, work: WorkInfo?): JComponent? {
    if (work == null) return splitter.secondComponent

    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Work Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 5, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    val closeButton = CloseButton(ActionListener {
      splitter.secondComponent = null
      selectedModelRow = -1
    })
    headingPanel.add(closeButton, BorderLayout.EAST)

    val panel = JPanel(TabularLayout("*", "Fit,Fit,*"))
    panel.add(headingPanel, TabularLayout.Constraint(0, 0))
    panel.add(JSeparator(), TabularLayout.Constraint(1, 0))
    val detailPanel = ScrollablePanel(VerticalLayout(18))
    detailPanel.border = BorderFactory.createEmptyBorder(6, 12, 20, 12)

    val idListProvider = IdListProvider(client, table, work)
    detailPanel.preferredScrollableViewportSize
    val scrollPane = JBScrollPane(detailPanel)
    scrollPane.border = BorderFactory.createEmptyBorder()
    detailPanel.add(buildCategoryPanel("Description", listOf(
      buildKeyValuePair("Class", work.workerClassName, classNameProvider),
      buildKeyValuePair("Tags", work.tagsList, StringListProvider),
      buildKeyValuePair("UUID", work.id)
    )))

    detailPanel.add(buildCategoryPanel("Execution", listOf(
      buildKeyValuePair("Enqueued by", work.callStack, enqueuedAtProvider),
      buildKeyValuePair("Constraints", work.constraints, ConstraintProvider),
      buildKeyValuePair("Frequency", if (work.isPeriodic) "Periodic" else "One Time"),
      buildKeyValuePair("State", work.state, StateProvider)
    )))

    detailPanel.add(buildCategoryPanel("WorkContinuation", listOf(
      buildKeyValuePair("Previous", work.prerequisitesList.toList(), idListProvider),
      buildKeyValuePair("Next", work.dependentsList.toList(), idListProvider),
      buildKeyValuePair("Unique work chain", client.getWorkChain(work.id), idListProvider)
    )))

    detailPanel.add(buildCategoryPanel("Results", listOf(
      buildKeyValuePair("Time Started", work.scheduleRequestedAt, TimeProvider),
      buildKeyValuePair("Retries", work.runAttemptCount),
      buildKeyValuePair("Output Data", work, OutputDataProvider)
    )))

    panel.add(scrollPane, TabularLayout.Constraint(2, 0))
    return panel
  }

  private fun buildCategoryPanel(name: String, subPanels: List<Component>): JPanel {
    val panel = JPanel(VerticalLayout(0))

    val headingPanel = TitledSeparator(name)
    panel.add(headingPanel)

    for (subPanel in subPanels) {
      val borderedPanel = JPanel(BorderLayout())
      borderedPanel.add(subPanel, BorderLayout.WEST)
      borderedPanel.border = BorderFactory.createEmptyBorder(0, 20, 0, 0)
      panel.add(borderedPanel)
    }
    return panel
  }

  private fun <T> buildKeyValuePair(key: String,
                                    value: T,
                                    componentProvider: ComponentProvider<T> = ToStringProvider()): JPanel {
    val panel = JPanel(TabularLayout("180px,*"))
    val keyPanel = JPanel(BorderLayout())
    keyPanel.add(JBLabel(key), BorderLayout.NORTH) // If value is multi-line, key should stick to the top of its cell
    panel.add(keyPanel, TabularLayout.Constraint(0, 0))
    panel.add(componentProvider.convert(value), TabularLayout.Constraint(0, 1))
    return panel
  }

  /**
   * @return a list of actions from the drop down menu that filter works with a tag.
   */
  @TestOnly
  fun getFilterActionList(): List<ToggleAction> {
    val toolbar = TreeWalker(component).descendantStream().filter { it is ActionToolbar }.findFirst().get() as ActionToolbarImpl
    val selectFilterAction = toolbar.actions[2] as TagsDropDownAction
    selectFilterAction.updateActions(DataContext.EMPTY_CONTEXT)
    return selectFilterAction.getChildren(null).map { it as ToggleAction }
  }
}

class CloseButton(actionListener: ActionListener?) : InplaceButton(
  IconButton("Close", AllIcons.Ide.Notification.Close,
             AllIcons.Ide.Notification.CloseHover), actionListener) {

  init {
    preferredSize = BUTTON_DIMENS
    minimumSize = preferredSize // Prevent layout phase from squishing this button
  }
}
