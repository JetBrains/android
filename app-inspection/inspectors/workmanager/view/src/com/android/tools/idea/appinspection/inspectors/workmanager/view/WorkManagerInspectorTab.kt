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

import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.adtui.HoverRowTable
import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorksTableModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
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
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

  private class WorksTableCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component =
      when (table?.convertColumnIndexToModel(column)) {
        // TODO(163343710): Add icons on the left of state text
        WorksTableModel.Column.STATE.ordinal -> {
          val text = WorkInfo.State.forNumber(value as Int).name
          val capitalizedText = text[0] + text.substring(1).toLowerCase(Locale.getDefault())
          super.getTableCellRendererComponent(table, capitalizedText, isSelected, hasFocus, row, column)
        }
        WorksTableModel.Column.TIME_STARTED.ordinal -> {
          val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
          val time = value as Long
          val timeText = if (time == -1L) "-" else formatter.format(Date(value))
          super.getTableCellRendererComponent(table, timeText, isSelected, hasFocus, row, column)
        }
        else -> super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      }
  }

  private inner class CancelAction : AnAction("Cancel Selected Work", "", AllIcons.Actions.Suspend) {

    override fun actionPerformed(e: AnActionEvent) {
      val id = client.getWorkInfoOrNull(selectedModelRow)?.id
      if (id != null) {
        client.cancelWorkById(id)
      }
    }
  }

  private inner class SelectTagAction :
    DropDownAction("All tags", "Select tag to filter", null) {
    private var lastTag: String? = null

    override fun update(event: AnActionEvent) {
      if (lastTag != client.filterTag) {
        event.presentation.text = client.filterTag
      }
    }

    override fun updateActions(context: DataContext): Boolean {
      removeAll()
      add(TagFilterAction(null))
      client.getAllTags().forEach { tag ->
        add(TagFilterAction(tag))
      }
      return true
    }

    override fun displayTextInToolbar() = true
  }

  private inner class TagFilterAction(private val tag: String?)
    : ToggleAction(tag ?: "All tags") {
    override fun isSelected(event: AnActionEvent): Boolean {
      return tag == client.filterTag
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      client.filterTag = tag
    }
  }

  private val classNameProvider = ClassNameProvider(ideServices, scope)
  private val timeProvider = TimeProvider()
  private val enqueuedAtProvider = EnqueuedAtProvider(ideServices, scope)
  private val stringListProvider = StringListProvider()
  private val constraintProvider = ConstraintProvider()
  private val outputDataProvider = OutputDataProvider()

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
      add(SelectTagAction())
    }
    val toolbar = ActionManager.getInstance().createActionToolbar("WorkManagerInspector", group, true)
    return toolbar.component
  }

  private fun buildWorksTable(): JBTable {
    val model = WorksTableModel(client)
    val table: JBTable = HoverRowTable(model)

    table.autoCreateRowSorter = true
    table.setDefaultRenderer(Object::class.java, WorksTableCellRenderer())

    // Adjusts width for each column.
    table.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        for (column in WorksTableModel.Column.values()) {
          table.columnModel.getColumn(column.ordinal).preferredWidth = (table.width * column.widthPercentage).toInt()
        }
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
      buildKeyValuePair("Tags", work.tagsList, stringListProvider),
      buildKeyValuePair("UUID", work.id)
    )))

    detailPanel.add(buildCategoryPanel("Execution", listOf(
      buildKeyValuePair("Enqueued by", work.callStack, enqueuedAtProvider),
      buildKeyValuePair("Constraints", work.constraints, constraintProvider),
      buildKeyValuePair("Frequency", if (work.isPeriodic) "Periodic" else "One Time"),
      buildKeyValuePair("State", work.state.name)
    )))

    detailPanel.add(buildCategoryPanel("WorkContinuation", listOf(
      buildKeyValuePair("Previous", work.prerequisitesList.toList(), idListProvider),
      buildKeyValuePair("Next", work.dependentsList.toList(), idListProvider),
      buildKeyValuePair("Unique work chain", client.getWorkChain(work.id), idListProvider)
    )))

    detailPanel.add(buildCategoryPanel("Results", listOf(
      buildKeyValuePair("Time Started", work.scheduleRequestedAt, timeProvider),
      buildKeyValuePair("Retries", work.runAttemptCount),
      buildKeyValuePair("Output Data", work.data, outputDataProvider)
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
}

class CloseButton(actionListener: ActionListener?) : InplaceButton(
  IconButton("Close", AllIcons.Ide.Notification.Close,
             AllIcons.Ide.Notification.CloseHover), actionListener) {

  init {
    preferredSize = BUTTON_DIMENS
    minimumSize = preferredSize // Prevent layout phase from squishing this button
  }
}
