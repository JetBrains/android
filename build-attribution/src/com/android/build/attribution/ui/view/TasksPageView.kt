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
package com.android.build.attribution.ui.view

import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.build.attribution.ui.model.TasksPageId
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.build.attribution.ui.model.tasksFilterComponent
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.view.chart.TimeDistributionTreeChart
import com.android.build.attribution.ui.view.details.TaskViewDetailPagesFactory
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import icons.StudioIcons
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.event.ItemEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

@NonNls
private const val SPLITTER_PROPERTY = "BuildAnalyzer.TasksView.Splitter.Proportion"

/**
 * Tasks view of Build Analyzer report that is based on ComboBoxes navigation on the top level.
 */
class TasksPageView(
  val model: TasksDataPageModel,
  val actionHandlers: ViewActionHandlers,
  val disposable: Disposable,
  val detailPagesFactory: TaskViewDetailPagesFactory = TaskViewDetailPagesFactory(model, actionHandlers)
) : BuildAnalyzerDataPageView {

  // Flag to prevent triggering calls to action handler on pulled from the model updates.
  private var fireActionHandlerEvents = true

  val groupingCheckBox = JCheckBox("Group by plugin", false).apply {
    name = "tasksGroupingCheckBox"
    addActionListener { event ->
      if (fireActionHandlerEvents) {
        val grouping = if (isSelected) TasksDataPageModel.Grouping.BY_PLUGIN else TasksDataPageModel.Grouping.UNGROUPED
        actionHandlers.tasksGroupingSelectionUpdated(grouping)
      }
    }
  }

  val tasksGroupingComboBox = ComboBox(
    CollectionComboBoxModel(model.availableGroupings, TasksDataPageModel.Grouping.BY_TASK_CATEGORY)
  ).apply {
    name = "tasksGroupingComboBox"
    renderer = SimpleListCellRenderer.create { label, value, _ -> label.text = value.uiName }
    addItemListener { event ->
      if (fireActionHandlerEvents && event.stateChange == ItemEvent.SELECTED) {
        actionHandlers.tasksGroupingSelectionUpdated(event.item as TasksDataPageModel.Grouping)
      }
    }
  }

  val tree = Tree(DefaultTreeModel(model.treeRoot)).apply {
    isRootVisible = false
    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    BuildAnalyzerMasterTreeCellRenderer.install(this)
    TreeSpeedSearch(this, true, TreeSpeedSearch.NODE_PRESENTATION_FUNCTION).apply {
      comparator = SpeedSearchComparator(false)
    }
    TreeUtil.installActions(this)
    addTreeSelectionListener { e ->
      if (fireActionHandlerEvents) {
        actionHandlers.tasksTreeNodeSelected(e?.newLeadSelectionPath?.lastPathComponent as? TasksTreeNode)
      }
    }
  }

  val treeHeaderLabel: JLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD) }
  val tasksLegendPanel = JPanel().apply {
    border = JBUI.Borders.emptyRight(5)
    layout = HorizontalLayout(10)
    isOpaque = false
    fun legendItem(name: String, tooltip: String, color: Color) = JBLabel(name, ColorIcon(10, color), SwingConstants.RIGHT).apply {
      toolTipText = tooltip
    }
    add(legendItem(
      name = "Android/Java/Kotlin Plugin",
      tooltip = "The task belongs to the Android Gradle plugin, Java Gradle plugin, or Kotlin Gradle plugin",
      color = CriticalPathChartLegend.androidPluginColor.baseColor
    ), HorizontalLayout.RIGHT)
    add(legendItem(
      name = "Other Plugin",
      tooltip = "The task belongs to a third-party or custom plugin, such as a plugin you applied after creating a new project using Android Studio",
      color = CriticalPathChartLegend.externalPluginColor.baseColor
    ), HorizontalLayout.RIGHT)
    add(legendItem(
      name = "Project Customization",
      tooltip = "The task does not belong to a plugin. For example, task that you might define within your build.gradle files",
      color = CriticalPathChartLegend.buildscriptPluginColor.baseColor
    ), HorizontalLayout.RIGHT)
  }

  val detailsPanel = object : CardLayoutPanel<TasksPageId, TasksPageId, JComponent>() {
    override fun prepare(key: TasksPageId): TasksPageId = key

    override fun create(pageId: TasksPageId): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
      name = "details-${pageId}"
      val scrollPane = JBScrollPane().apply {
        border = JBUI.Borders.empty()
        setViewportView(detailPagesFactory.createDetailsPage(pageId))
      }
      add(scrollPane, BorderLayout.CENTER)
    }
  }

  private val componentsSplitter = OnePixelSplitter(SPLITTER_PROPERTY, 0.33f).apply {
    val masterHalf: JComponent = JBPanel<JBPanel<*>>().apply {
      layout = BorderLayout(2, 1)
      val treeHeaderPanel = JPanel().apply {
        layout = BorderLayout()
        background = UIUtil.getTreeBackground()

        val helpIcon = JLabel(StudioIcons.Common.HELP).apply {
          HelpTooltip()
            .setDescription("Build Analyzer only includes tasks that are part of the <b>critical path</b> for this build." +
                            " These are the tasks you should investigate to optimize your build.")
            .installOn(this)
        }

        add(
          JBPanel<JBPanel<*>>(HorizontalLayout(5)).apply {
            border = JBUI.Borders.empty(5, 20)
            add(treeHeaderLabel)
            add(helpIcon)
          },
          BorderLayout.CENTER
        )
        add(tasksLegendPanel, BorderLayout.SOUTH)
      }
      add(treeHeaderPanel, BorderLayout.NORTH)
      CriticalPathChartLegend.pluginColorPalette.reset()
      val treeComponent = TimeDistributionTreeChart.wrap(tree)

      add(treeComponent, BorderLayout.CENTER)
    }
    val detailsHalf: JPanel = JPanel().apply {
      val dimension = JBUI.size(5, 5)
      layout = BorderLayout(dimension.width(), dimension.height())
      border = JBUI.Borders.empty(5, 20)
      add(detailsPanel, BorderLayout.CENTER)
    }

    firstComponent = masterHalf
    secondComponent = detailsHalf
    setHonorComponentsMinimumSize(true)
  }

  override val component: JPanel = JBPanelWithEmptyText(BorderLayout()).apply {
    name = "tasks-view"
    add(componentsSplitter, BorderLayout.CENTER)
    componentsSplitter.isVisible = !model.isEmpty
    emptyText.apply {
      appendLine("This build ran without any tasks to process, or all tasks were already up to date.")
      appendLine("Learn more about this build's performance:")
      appendLine("All warnings", SimpleTextAttributes.LINK_ATTRIBUTES) {
        actionHandlers.changeViewToWarningsLinkClicked()
      }
    }
  }

  override val additionalControls: JPanel = JPanel().apply {
    layout = HorizontalLayout(10)
    name = "tasks-view-additional-controls"

    if (model.reportData.showTaskCategoryInfo) {
      add(JLabel("Group by:"))
      add(tasksGroupingComboBox)
    } else {
      add(groupingCheckBox)
    }
    add(tasksFilterComponent(model, actionHandlers, disposable))
  }

  init {
    updateViewFromModel(true)
    model.addModelUpdatedListener(disposable, this::updateViewFromModel)
  }

  private fun updateViewFromModel(treeStructureChanged: Boolean) {
    fireActionHandlerEvents = false
    if (model.reportData.showTaskCategoryInfo) {
      tasksGroupingComboBox.selectedItem = model.selectedGrouping
    } else {
      groupingCheckBox.isSelected = model.selectedGrouping == TasksDataPageModel.Grouping.BY_PLUGIN
    }
    treeHeaderLabel.text = model.treeHeaderText
    tasksLegendPanel.isVisible = model.selectedGrouping == TasksDataPageModel.Grouping.UNGROUPED
    if (treeStructureChanged) {
      (tree.model as DefaultTreeModel).setRoot(model.treeRoot)
      // Need to remove cached details pages as content might change on tree structure change, especially for grouping nodes.
      detailsPanel.removeAll()
    }
    model.selectedNode.let { selectedNode ->
      if (selectedNode == null) {
        val emptyPageId = TasksPageId.emptySelection(model.selectedGrouping)
        detailsPanel.select(emptyPageId, true)
        tree.selectionModel.clearSelection()
      }
      else {
        detailsPanel.select(selectedNode.descriptor.pageId, true)
        TreeUtil.selectNode(tree, selectedNode)
      }
    }
    fireActionHandlerEvents = true
  }
}