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
import com.android.build.attribution.ui.model.tasksFilterActions
import com.android.build.attribution.ui.panels.CriticalPathChartLegend
import com.android.build.attribution.ui.view.chart.TimeDistributionTreeChart
import com.android.build.attribution.ui.view.details.ChartsPanel
import com.android.build.attribution.ui.view.details.TaskViewDetailPagesFactory
import com.android.tools.idea.flags.StudioFlags.NEW_BUILD_ANALYZER_UI_VISUALIZATION_ENABLED
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.render.RenderingHelper.SHRINK_LONG_RENDERER
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.RepaintManager
import javax.swing.ScrollPaneConstants
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

  val tree = Tree(DefaultTreeModel(model.treeRoot)).apply {
    isRootVisible = false
    selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    BuildAnalyzerMasterTreeCellRenderer.install(this)
    TreeSpeedSearch(this, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true).apply {
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
    fun legendItem(name: String, color: Color) = JBLabel(name, ColorIcon(10, color), SwingConstants.RIGHT)
    add(legendItem("Android/Java/Kotlin Plugin", CriticalPathChartLegend.androidPluginColor.baseColor), HorizontalLayout.RIGHT)
    add(legendItem("Other Plugin", CriticalPathChartLegend.externalPluginColor.baseColor), HorizontalLayout.RIGHT)
    add(legendItem("Project Customization", CriticalPathChartLegend.buildsrcPluginColor.baseColor), HorizontalLayout.RIGHT)
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

  val chartsPanel = ChartsPanel(model.reportData)

  private val componentsSplitter = OnePixelSplitter(SPLITTER_PROPERTY, 0.33f).apply {
    val masterHalf: JComponent = JBPanel<JBPanel<*>>().apply {
      layout = BorderLayout(2, 1)
      val treeHeaderPanel = JPanel().apply {
        layout = BorderLayout()
        background = UIUtil.getTreeBackground()
        treeHeaderLabel.border = JBUI.Borders.empty(5, 20)
        treeHeaderLabel.alignmentX = Component.LEFT_ALIGNMENT
        add(treeHeaderLabel, BorderLayout.CENTER)
        if (NEW_BUILD_ANALYZER_UI_VISUALIZATION_ENABLED.get()) {
          add(tasksLegendPanel, BorderLayout.SOUTH)
        }
      }
      add(treeHeaderPanel, BorderLayout.NORTH)
      val treeComponent: Component = if (NEW_BUILD_ANALYZER_UI_VISUALIZATION_ENABLED.get()) {
        // Create tree with new visualization element attached when flag is on.
        CriticalPathChartLegend.pluginColorPalette.reset()
        TimeDistributionTreeChart.wrap(tree)
      }
      else ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE)

      add(treeComponent, BorderLayout.CENTER)
    }
    val detailsHalf: JPanel = JPanel().apply {
      val dimension = JBUI.size(5, 5)
      layout = BorderLayout(dimension.width(), dimension.height())
      border = JBUI.Borders.empty(5, 20)
      add(detailsPanel, BorderLayout.CENTER)
      if (!NEW_BUILD_ANALYZER_UI_VISUALIZATION_ENABLED.get()) {
        // Add old visualization to the UI only if flag is off.
        val chartsScrollArea = JBScrollPane().apply {
          border = JBUI.Borders.empty()
          horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
          verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
          verticalScrollBar.isOpaque = false

          setViewportView(chartsPanel)
        }
        add(chartsScrollArea, BorderLayout.WEST)
      }
    }

    firstComponent = masterHalf
    secondComponent = detailsHalf
    setHonorComponentsMinimumSize(true)
  }

  override val component: JPanel = JPanel(BorderLayout()).apply {
    name = "tasks-view"
    if (model.isEmpty) {
      add(createEmptyStatePanel(), BorderLayout.CENTER)
    }
    else {
      add(componentsSplitter, BorderLayout.CENTER)
    }
  }

  override val additionalControls: JPanel = JPanel().apply {
    layout = HorizontalLayout(10)
    name = "tasks-view-additional-controls"

    val tasksFilterActions = tasksFilterActions(model, actionHandlers)
    val defaultActionGroup = DefaultActionGroup().apply { add(tasksFilterActions) }
    val actionsToolbar = ActionManager.getInstance().createActionToolbar("BuildAnalyzerView", defaultActionGroup, true)

    add(groupingCheckBox)
    if (NEW_BUILD_ANALYZER_UI_VISUALIZATION_ENABLED.get()) {
      add(actionsToolbar.component)
    }
  }

  init {
    updateViewFromModel(true)
    model.setModelUpdatedListener(this::updateViewFromModel)
  }

  private fun updateViewFromModel(treeStructureChanged: Boolean) {
    fireActionHandlerEvents = false
    groupingCheckBox.isSelected = model.selectedGrouping == TasksDataPageModel.Grouping.BY_PLUGIN
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
        chartsPanel.select(emptyPageId, true)
      }
      else {
        detailsPanel.select(selectedNode.descriptor.pageId, true)
        chartsPanel.select(selectedNode.descriptor.pageId, true)
        TreeUtil.selectNode(tree, selectedNode)
      }
    }
    fireActionHandlerEvents = true
  }

  private fun createEmptyStatePanel() = JPanel().apply {
    name = "empty-state"
    border = JBUI.Borders.empty(20)
    layout = VerticalLayout(0, SwingConstants.LEFT)

    add(JBLabel("This build did not run any tasks or all of the tasks were up to date."))
    add(JBLabel("To continue exploring this build's performance, consider these views into this build."))
    add(JPanel().apply {
      name = "links"
      border = JBUI.Borders.emptyTop(20)
      layout = VerticalLayout(0, SwingConstants.LEFT)
      add(HyperlinkLabel("All warnings").apply {
        addHyperlinkListener { actionHandlers.changeViewToWarningsLinkClicked() }
      })
    })
  }
}