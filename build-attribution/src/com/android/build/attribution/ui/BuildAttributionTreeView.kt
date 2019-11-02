/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.tree.AbstractBuildAttributionNode
import com.android.build.attribution.ui.tree.BuildAttributionNodeRenderer
import com.android.build.attribution.ui.tree.BuildSummaryNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.CachingSimpleNode
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.CardLayout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

@NonNls
private const val SPLITTER_PROPERTY = "BuildAttribution.Splitter.Proportion"

class BuildAttributionTreeView(
  project: Project,
  private val reportData: BuildAttributionReportUiData
) : ComponentContainer, TreeNodeSelector {

  private val disposed = AtomicBoolean()
  private val rootNode = RootNode()
  private val treeModel: StructureTreeModel<SimpleTreeStructure>
  private val panel = JPanel()
  private val tree: Tree
  private val handler: InfoViewHandler

  val isDisposed: Boolean
    get() = disposed.get()

  init {
    Disposer.register(project, this)

    val treeStructure = SimpleTreeStructure.Impl(rootNode)
    treeModel = StructureTreeModel(treeStructure, this)
    tree = initTree(AsyncTreeModel(treeModel, this))

    panel.layout = BorderLayout()
    val componentsSplitter = OnePixelSplitter(SPLITTER_PROPERTY, 0.33f)
    componentsSplitter.setHonorComponentsMinimumSize(true)
    componentsSplitter.firstComponent = JPanel(CardLayout()).apply {
      add(ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE), "Tree")
    }
    handler = InfoViewHandler(tree)
    componentsSplitter.secondComponent = handler.component
    panel.add(componentsSplitter, BorderLayout.CENTER)

    TreeUtil.selectFirstNode(tree)
  }

  private fun initTree(model: AsyncTreeModel): Tree {
    val tree = Tree(model)
    tree.isRootVisible = false
    TreeSpeedSearch(tree).comparator = SpeedSearchComparator(false)
    TreeUtil.installActions(tree)
    tree.cellRenderer = BuildAttributionNodeRenderer()
    return tree
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusableComponent(): JComponent = tree

  override fun dispose() = disposed.set(true)

  override fun selectNode(node: SimpleNode) {
    treeModel.select(node, tree) { t: TreePath? ->
      Logger.getInstance(BuildAttributionTreeView::class.java).debug("Path selected with link: ${t}")
    }
  }

  /**
   * This class updates info shown on the right in response to tree nodes selection.
   */
  private class InfoViewHandler(tree: Tree) {
    private val viewMap = ContainerUtil.newConcurrentMap<String, JComponent>()
    private val enabledViewRef = AtomicReference<String>()
    private val panel: JPanel = JPanel(CardLayout())

    val component: JComponent
      get() = panel

    init {
      tree.addTreeSelectionListener { e ->
        if (e.path != null || e.isAddedPath) {
          updateViewFromNode(tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)
        }
      }
      tree.selectionPath?.let {
        updateViewFromNode(it.lastPathComponent as DefaultMutableTreeNode)
      }
    }

    private fun updateViewFromNode(node: DefaultMutableTreeNode?) {
      node?.userObject?.let { selectedNode ->
        if (selectedNode is AbstractBuildAttributionNode) {
          val name = selectedNode.nodeId
          if (name == enabledViewRef.get()) {
            return
          }
          if (!viewMap.containsKey(name)) {
            val infoPanel = wrapInfoPanel(selectedNode.component)
            viewMap[name] = infoPanel
            panel.add(infoPanel, name)
          }

          enabledViewRef.set(name)
          (panel.layout as CardLayout).show(panel, name)
        }
      }
    }

    private fun wrapInfoPanel(infoPanel: JComponent): JComponent = JBScrollPane(infoPanel).apply {
      border = BorderFactory.createEmptyBorder()
    }
  }

  private inner class RootNode : CachingSimpleNode(null) {

    override fun buildChildren(): Array<SimpleNode> {
      val nodes = mutableListOf<SimpleNode>()
      nodes.add(BuildSummaryNode(reportData.buildSummary, this))
      return nodes.toTypedArray()
    }
  }
}

interface TreeNodeSelector {
  fun selectNode(node: SimpleNode)
}
