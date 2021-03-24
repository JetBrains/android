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
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.common.showViewContextMenu
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.IconProvider
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.treeStructure.Tree
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane

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

  @VisibleForTesting
  val componentTreeSelectionModel: ComponentTreeSelectionModel

  init {
    val builder = ComponentTreeBuilder()
      .withHiddenRoot()
      .withAutoScroll()
      .withNodeType(nodeType)
      .withContextMenu(::showPopup)
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }
      .withHorizontalScrollBar()
      .withComponentName("inspectorComponentTree")

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
    tree?.setDefaultPainter()
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

  override fun getGearActions(): List<AnAction> {
    return if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_COMPONENT_TREE_OPTIONS.get()) {
      listOf(CallstackAction, DrawablesInCallstackAction, CompactTree, SupportLines)
    }
    else {
      listOf()
    }
  }

  override fun getAdditionalActions() = listOf(FilterNodeAction)

  override fun getComponent() = componentTree

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
    updateHierarchy(window.root, temp)
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

  private fun updateHierarchy(node: ViewNode, parent: TreeViewNode) {
    val nextParent = if (!node.isInComponentTree) parent else {
      val treeNode = node.treeNode
      parent.children.add(treeNode)
      treeNode.parent = parent
      treeNode.children.clear()
      if (node.isSingleCall) parent else treeNode
    }
    node.children.forEach { updateHierarchy(it, nextParent) }
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
