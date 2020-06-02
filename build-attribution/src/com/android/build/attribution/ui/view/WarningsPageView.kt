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
import com.android.build.attribution.ui.model.WarningsDataPageModel
import com.android.build.attribution.ui.model.WarningsTreeNode
import com.android.build.attribution.ui.model.WarningsTreePresentableNodeDescriptor
import com.android.build.attribution.ui.view.details.WarningsViewDetailPagesFactory
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
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultTreeModel


@NonNls
private const val SPLITTER_PROPERTY = "BuildAnalyzer.WarningsView.Splitter.Proportion"

/**
 * Warnings view of Build Analyzer report that is based on ComboBoxes navigation on the top level.
 */
class WarningsPageView(
  val model: WarningsDataPageModel,
  val actionHandlers: ViewActionHandlers,
  val detailPagesFactory: WarningsViewDetailPagesFactory = WarningsViewDetailPagesFactory(actionHandlers)
) : BuildAnalyzerDataPageView {

  // Flag to prevent triggering calls to action handler on pulled from the model updates.
  private var fireActionHandlerEvents = true

  override val additionalControls = JPanel().apply { name = "warnings-view-additional-controls" }

  val tree = Tree(DefaultTreeModel(model.treeRoot)).apply {
    isRootVisible = false
    cellRenderer = BuildAnalyzerMasterTreeCellRenderer()
    TreeSpeedSearch(this, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true).apply {
      comparator = SpeedSearchComparator(false)
    }
    TreeUtil.installActions(this)
    addTreeSelectionListener { e ->
      if (fireActionHandlerEvents && e.path != null && e.isAddedPath) {
        (e.path.lastPathComponent as? WarningsTreeNode)?.let {
          actionHandlers.warningsTreeNodeSelected(it)
        }
      }
    }
  }

  val treeHeaderLabel: JLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD) }

  val detailsPanel = object : CardLayoutPanel<WarningsTreePresentableNodeDescriptor, WarningsTreePresentableNodeDescriptor, JComponent>() {
    override fun prepare(key: WarningsTreePresentableNodeDescriptor): WarningsTreePresentableNodeDescriptor = key

    override fun create(node: WarningsTreePresentableNodeDescriptor): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
      name = "details-${node.pageId}"
      val scrollPane = JBScrollPane().apply {
        border = JBUI.Borders.empty()
        setViewportView(detailPagesFactory.createDetailsPage(node))
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
    }

    firstComponent = masterHalf
    secondComponent = detailsHalf
    setHonorComponentsMinimumSize(true)
  }

  override val component: JPanel = JPanel(BorderLayout()).apply {
    name = "warnings-view"
    if (model.isEmpty) {
      add(createEmptyStatePanel(), BorderLayout.CENTER)
    }
    else {
      add(componentsSplitter, BorderLayout.CENTER)
    }
  }

  init {
    updateViewFromModel()
    model.setModelUpdatedListener(this::updateViewFromModel)
  }

  private fun updateViewFromModel() {
    fireActionHandlerEvents = false
    treeHeaderLabel.text = model.treeHeaderText
    if (tree.model.root != model.treeRoot) {
      (tree.model as DefaultTreeModel).setRoot(model.treeRoot)
    }
    model.selectedNode?.let {
      detailsPanel.select(it.descriptor, true)
      TreeUtil.selectNode(tree, it)
    }
    fireActionHandlerEvents = true
  }

  private fun createEmptyStatePanel() = JPanel().apply {
    name = "empty-state"
    border = JBUI.Borders.empty(20)
    layout = VerticalLayout(0, SwingConstants.LEFT)

    add(JBLabel("This build does not have any warnings."))
    add(JBLabel("To continue exploring this build's performance, consider these views into this build."))
    add(JPanel().apply {
      name = "links"
      border = JBUI.Borders.emptyTop(20)
      layout = VerticalLayout(0, SwingConstants.LEFT)
      add(HyperlinkLabel("Tasks impacting build duration").apply {
        addHyperlinkListener { actionHandlers.changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.UNGROUPED) }
      })
      add(HyperlinkLabel("Plugins with tasks impacting build duration").apply {
        addHyperlinkListener { actionHandlers.changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.BY_PLUGIN) }
      })
    })
  }
}