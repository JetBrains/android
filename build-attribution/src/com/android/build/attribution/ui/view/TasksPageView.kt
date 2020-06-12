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
import com.android.build.attribution.ui.model.TasksTreeNode
import com.android.build.attribution.ui.model.TasksTreePresentableNodeDescriptor
import com.android.build.attribution.ui.view.details.ChartsPanel
import com.android.build.attribution.ui.view.details.TaskViewDetailPagesFactory
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
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultTreeModel

@NonNls
private const val SPLITTER_PROPERTY = "BuildAnalyzer.TasksView.Splitter.Proportion"

/**
 * Tasks view of Build Analyzer report that is based on ComboBoxes navigation on the top level.
 */
class TasksPageView(
  val model: TasksDataPageModel,
  val actionHandlers: ViewActionHandlers,
  val detailPagesFactory: TaskViewDetailPagesFactory = TaskViewDetailPagesFactory(actionHandlers)
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
    cellRenderer = BuildAnalyzerMasterTreeCellRenderer()
    TreeSpeedSearch(this, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true).apply {
      comparator = SpeedSearchComparator(false)
    }
    TreeUtil.installActions(this)
    addTreeSelectionListener { e ->
      if (fireActionHandlerEvents && e.path != null && e.isAddedPath) {
        (e.path.lastPathComponent as? TasksTreeNode)?.let {
          actionHandlers.tasksTreeNodeSelected(it)
        }
      }
    }
  }

  val treeHeaderLabel: JLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD) }

  val detailsPanel = object : CardLayoutPanel<TasksTreePresentableNodeDescriptor, TasksTreePresentableNodeDescriptor, JComponent>() {
    override fun prepare(key: TasksTreePresentableNodeDescriptor): TasksTreePresentableNodeDescriptor = key

    override fun create(node: TasksTreePresentableNodeDescriptor): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
      name = "details-${node.pageId}"
      val scrollPane = JBScrollPane().apply {
        border = JBUI.Borders.empty()
        setViewportView(detailPagesFactory.createDetailsPage(node))
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
      }
      add(treeHeaderPanel, BorderLayout.NORTH)
      add(ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE), BorderLayout.CENTER)
    }
    val detailsHalf: JPanel = JPanel().apply {
      val dimension = JBUI.size(20, 5)
      layout = BorderLayout(dimension.width(), dimension.height())
      border = JBUI.Borders.empty(5, 20)
      add(detailsPanel, BorderLayout.CENTER)
      val chartsScrollArea = JBScrollPane(chartsPanel).apply {
        border = JBUI.Borders.empty()
        setViewportView(chartsPanel)
      }
      add(chartsScrollArea, BorderLayout.WEST)
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
    add(groupingCheckBox)
  }

  init {
    updateViewFromModel()
    model.setModelUpdatedListener(this::updateViewFromModel)
  }

  private fun updateViewFromModel() {
    fireActionHandlerEvents = false
    groupingCheckBox.isSelected = model.selectedGrouping == TasksDataPageModel.Grouping.BY_PLUGIN
    treeHeaderLabel.text = model.treeHeaderText
    if (tree.model.root != model.treeRoot) {
      (tree.model as DefaultTreeModel).setRoot(model.treeRoot)
    }
    model.selectedNode?.let {
      detailsPanel.select(it.descriptor, true)
      chartsPanel.select(it.descriptor.pageId, true)
      TreeUtil.selectNode(tree, it)
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