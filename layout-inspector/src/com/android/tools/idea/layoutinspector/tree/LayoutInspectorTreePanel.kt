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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.IconProvider
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.ui.LINES
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.CommonActionsManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.treeStructure.Tree
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.Collections
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.max

fun AnActionEvent.treePanel(): LayoutInspectorTreePanel? =
  ToolContent.getToolContent(this.getData(PlatformDataKeys.CONTEXT_COMPONENT)) as? LayoutInspectorTreePanel

fun AnActionEvent.tree(): Tree? = treePanel()?.tree

class LayoutInspectorTreePanel(parentDisposable: Disposable) : ToolContent<LayoutInspector> {
  private var layoutInspector: LayoutInspector? = null
  private val componentTree: JComponent
  private val componentTreeModel: ComponentTreeModel
  private val nodeType = InspectorViewNodeType()
  // synthetic node to hold the root of the tree.
  private var root: TreeViewNode = ViewNode("root").treeNode
  // synthetic node for computing new hierarchy.
  private val temp: TreeViewNode = ViewNode("temp").treeNode
  // a map from [AndroidWindow.id] to the root [TreeViewNode] of that window
  private val windowRoots = mutableMapOf<Any, MutableList<TreeViewNode>>()
  private val additionalActions: List<AnAction>
  private val comparator = SpeedSearchComparator(false)
  private var toolWindowCallback: ToolWindowCallback? = null
  private var filter = ""

  @VisibleForTesting
  val componentTreeSelectionModel: ComponentTreeSelectionModel

  init {
    val builder = ComponentTreeBuilder()
      .withHiddenRoot()
      .withAutoScroll()
      .withNodeType(nodeType)
      .withContextMenu(::showPopup)
      .withoutTreeSearch()
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }
      .withHorizontalScrollBar()
      .withComponentName("inspectorComponentTree")
      .withPainter { if (TreeSettings.supportLines) LINES else null }

    val (scrollPane, model, selectionModel) = builder.build()
    componentTree = scrollPane
    componentTreeModel = model
    componentTreeSelectionModel = selectionModel
    ActionManager.getInstance()?.getAction(IdeActions.ACTION_GOTO_DECLARATION)?.shortcutSet
      ?.let { GotoDeclarationAction.registerCustomShortcutSet(it, componentTree, parentDisposable) }
    selectionModel.addSelectionListener {
      layoutInspector?.layoutInspectorModel?.apply {
        val view = (it.firstOrNull() as? TreeViewNode)?.view
        setSelection(view, SelectionOrigin.COMPONENT_TREE)
        stats.selectionMadeFromComponentTree(view)
      }
    }
    layoutInspector?.layoutInspectorModel?.modificationListeners?.add { _, _, _ -> componentTree.repaint() }
    tree?.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(event: KeyEvent) {
        if (Character.isAlphabetic(event.keyChar.toInt())) {
          toolWindowCallback?.startFiltering(event.keyChar.toString())
        }
      }
    })
    val commonActionManager = CommonActionsManager.getInstance()
    additionalActions = listOf(
      FilterNodeAction,
      commonActionManager.createExpandAllHeaderAction(tree),
      commonActionManager.createCollapseAllHeaderAction(tree)
    )
  }

  val tree: Tree?
    get() = (component as? JScrollPane)?.viewport?.view as? Tree

  private fun showPopup(component: JComponent, x: Int, y: Int) {
    val node = componentTreeSelectionModel.currentSelection.singleOrNull() as TreeViewNode?
    if (node != null) {
      layoutInspector?.let { showViewContextMenu(listOf(node.view), it.layoutInspectorModel, component, x, y) }
    }
  }

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  override fun setToolContext(toolContext: LayoutInspector?) {
    layoutInspector?.layoutInspectorModel?.modificationListeners?.remove(this::modelModified)
    layoutInspector = toolContext
    nodeType.model = layoutInspector?.layoutInspectorModel
    layoutInspector?.layoutInspectorModel?.modificationListeners?.add(this::modelModified)
    componentTreeModel.treeRoot = root
    toolContext?.layoutInspectorModel?.selectionListeners?.add(this::selectionChanged)
  }

  override fun getGearActions() = listOf(CallstackAction, SupportLines)

  override fun getAdditionalActions() = additionalActions

  override fun getComponent() = componentTree

  override fun registerCallbacks(callback: ToolWindowCallback) {
    toolWindowCallback = callback
  }

  override fun supportsFiltering(): Boolean = true

  override fun setFilter(newFilter: String) {
    filter = newFilter
    val selection = tree?.selectionModel?.selectionPath?.lastPathComponent as? TreeViewNode
    val nodes = getNodes()
    val nodeCount = nodes.size
    val startIndex = max(0, nodes.indexOf(selection))
    for (i in 0 until nodeCount) {
      // Select the next matching node, wrapping at the last node, and starting with the current selection:
      if (matchAndSelectNode(nodes[Math.floorMod(startIndex + i, nodeCount)])) {
        return
      }
    }
  }

  private fun nextMatch() {
    val selection = tree?.selectionModel?.selectionPath?.lastPathComponent as? TreeViewNode
    val nodes = getNodes()
    val nodeCount = nodes.size
    val startIndex = max(0, nodes.indexOf(selection))
    for (i in 1..nodeCount) {
      // Select the next matching node, wrapping at the last node, and starting with the node just after the current selection:
      if (matchAndSelectNode(nodes[Math.floorMod(startIndex + i, nodeCount)])) {
        return
      }
    }
  }

  private fun previousMatch() {
    val selection = tree?.selectionModel?.selectionPath?.lastPathComponent as? TreeViewNode
    val nodes = getNodes()
    val nodeCount = nodes.size
    val startIndex = max(0, nodes.indexOf(selection))
    for (i in 1..nodeCount) {
      // Select the previous matching node, wrapping at the first node, and starting with the node just prior to the current selection:
      if (matchAndSelectNode(nodes[Math.floorMod(startIndex - i, nodeCount)])) {
        return
      }
    }
  }

  private fun matchAndSelectNode(node: TreeViewNode): Boolean {
    if (filter.isEmpty()) {
      return true
    }
    val name = node.view.qualifiedName
    val id = node.view.viewId?.name
    val text = node.view.textValue.ifEmpty { null }
    val searchString = listOfNotNull(name, id, text).joinToString(" - ")
    if (comparator.matchingFragments(filter, searchString) != null) {
      if (node !== tree?.selectionModel?.selectionPath?.lastPathComponent) {
        componentTreeSelectionModel.currentSelection = Collections.singletonList(node)
        layoutInspector?.layoutInspectorModel?.apply {
          setSelection(node.view, SelectionOrigin.COMPONENT_TREE)
          stats.selectionMadeFromComponentTree(node.view)
        }
      }
      return true
    }
    return false
  }

  private fun getNodes(): List<TreeViewNode> =
    root.flatten().minus(root).toList()

  override fun getFilterKeyListener() = filterKeyListener

  private val filterKeyListener = object : KeyAdapter() {
    override fun keyPressed(event: KeyEvent) {
      if (event.modifiersEx != 0) {
        return
      }
      when (event.keyCode) {
        KeyEvent.VK_DOWN -> {
          nextMatch()
          event.consume()
        }
        KeyEvent.VK_UP -> {
          previousMatch()
          event.consume()
        }
        KeyEvent.VK_ENTER -> {
          tree?.requestFocusInWindow()
          toolWindowCallback?.stopFiltering()
          event.consume()
        }
      }
    }
  }


  override fun dispose() {
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelModified(old: AndroidWindow?, new: AndroidWindow?, structuralChange: Boolean) {
    if (structuralChange) {
      var changedNode = new?.let { addToRoot(it) }
      if (windowRoots.keys.retainAll(layoutInspector?.layoutInspectorModel?.windows?.keys ?: emptyList())) {
        changedNode = root
      }
      if (changedNode == root) {
        rebuildRoot()
      }
      changedNode?.let { componentTreeModel.hierarchyChanged(it) }
    }
  }

  private fun addToRoot(window: AndroidWindow): TreeViewNode {
    temp.children.clear()
    updateHierarchy(window.root, temp, temp)
    temp.children.forEach { it.parent = root }
    val changedNode = temp.children.singleOrNull()
    val windowNodes = windowRoots.getOrPut(window.id) { mutableListOf() }
    if (changedNode != null && windowNodes.singleOrNull() === changedNode) {
      temp.children.clear()
      return changedNode
    }
    windowNodes.clear()
    windowNodes.addAll(temp.children)
    temp.children.clear()

    return root
  }

  private fun rebuildRoot() {
    root.children.clear()
    layoutInspector?.layoutInspectorModel?.windows?.keys?.flatMapTo(root.children) { id -> windowRoots[id] ?: emptyList() }
  }

  fun refresh() {
    windowRoots.clear()
    layoutInspector?.layoutInspectorModel?.windows?.values?.forEach { addToRoot(it) }
    rebuildRoot()
    componentTreeModel.hierarchyChanged(root)
  }

  private fun updateHierarchy(node: ViewNode, previous: TreeViewNode, parent: TreeViewNode) {
    val current = if (!node.isInComponentTree) previous else {
      val treeNode = node.treeNode
      parent.children.add(treeNode)
      treeNode.parent = parent
      treeNode.children.clear()
      treeNode
    }
    val nextParent = if (node.isSingleCall) parent else current
    node.children.forEach { updateHierarchy(it, current, nextParent) }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun selectionChanged(oldView: ViewNode?, newView: ViewNode?, origin: SelectionOrigin) {
    componentTreeSelectionModel.currentSelection = listOfNotNull(newView?.treeNode)
  }

  private class InspectorViewNodeType : ViewNodeType<TreeViewNode>() {
    var model: InspectorModel? = null
    override val clazz = TreeViewNode::class.java

    override fun tagNameOf(node: TreeViewNode) = node.view.qualifiedName

    override fun idOf(node: TreeViewNode) = node.view.viewId?.name

    override fun textValueOf(node: TreeViewNode) = node.view.textValue

    override fun iconOf(node: TreeViewNode): Icon =
      IconProvider.getIconForView(node.view.qualifiedName, node.view is ComposeViewNode)

    override fun parentOf(node: TreeViewNode): TreeViewNode? = node.parent

    override fun childrenOf(node: TreeViewNode): List<TreeViewNode> = node.children

    override fun isEnabled(node: TreeViewNode) = model?.isVisible(node.view) == true
  }
}
