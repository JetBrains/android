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
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.View
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Image
import java.util.Enumeration
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class LayoutInspectorTreePanel : ToolContent<LayoutInspector> {
  private var layoutInspector: LayoutInspector? = null
  private var client: InspectorClient? = null
  private val tree = Tree()
  private val contentPane = JBScrollPane(tree)

  init {
    contentPane.border = JBUI.Borders.empty()
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    tree.addTreeSelectionListener { e ->
      (e.newLeadSelectionPath?.lastPathComponent as MyTreeNode?)?.let {
        layoutInspector?.layoutInspectorModel?.selection = it.root
      }
    }
  }

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  override fun setToolContext(toolContext: LayoutInspector?) {
    layoutInspector?.layoutInspectorModel?.modificationListeners?.remove(this::modelModified)
    layoutInspector?.modelChangeListeners?.remove(this::modelChanged)
    layoutInspector = toolContext
    layoutInspector?.modelChangeListeners?.add(this::modelChanged)
    layoutInspector?.layoutInspectorModel?.modificationListeners?.add(this::modelModified)
    client = layoutInspector?.client
    client?.register(Common.Event.EventGroupIds.COMPONENT_TREE, ::loadComponentTree)
    if (toolContext != null) {
      modelChanged(toolContext.layoutInspectorModel, toolContext.layoutInspectorModel)
    }
  }

  override fun getComponent() = contentPane

  override fun dispose() {
  }

  private fun loadComponentTree(event: LayoutInspectorEvent) {
    val application = ApplicationManager.getApplication()
    application.executeOnPooledThread {
      val loader = ComponentTreeLoader(event.tree)
      val root = loader.loadRootView()
      val bytes = client?.getPayload(event.tree.payloadId) ?: return@executeOnPooledThread
      var viewRoot: InspectorView? = null
      if (bytes.isNotEmpty()) {
        viewRoot = SkiaParser().getViewTree(bytes)
      }
      if (viewRoot != null) {
        val imageLoader = ComponentImageLoader(root, viewRoot)
        imageLoader.loadImages()
      }

      application.invokeLater {
        layoutInspector?.layoutInspectorModel?.update(root)
      }
    }
  }

  class ComponentImageLoader(root: ViewNode, viewRoot: InspectorView) {
    private val nodeMap = root.flatten().associateBy { it.drawId }
    private val viewMap = viewRoot.flatten().associateBy { it.id.toLong() }

    fun loadImages() {
      for ((drawId, node) in nodeMap) {
        val view = viewMap[drawId] ?: continue
        node.imageBottom = view.image
        addChildNodeImages(node, view)
      }
    }

    private fun addChildNodeImages(node: ViewNode, view: InspectorView) {
      var beforeChildren = true
      for (child in view.children.values) {
        val isChildNode = view.id != child.id && nodeMap.containsKey(child.id.toLong())
        when {
          isChildNode -> beforeChildren = false
          beforeChildren -> node.imageBottom = combine(node.imageBottom, child)
          else -> node.imageTop = combine(node.imageTop, child)
        }
        if (!isChildNode) {
          // Some Skia views are several levels deep:
          addChildNodeImages(node, child)
        }
      }
    }

    private fun combine(image: Image?, view: InspectorView): Image? =
      when {
        view.image == null -> image
        image == null -> view.image
        else -> {
          // Combine the images...
          val g = image.graphics
          UIUtil.drawImage(g, view.image!!, 0, 0, null)
          g.dispose()
          image
        }
      }
  }

  private class ComponentTreeLoader(private val tree: ComponentTreeEvent) {
    val stringTable = StringTable(tree.stringList)

    fun loadRootView(): ViewNode {
      return loadView(tree.root, null)
    }

    fun loadView(view: View, parent: ViewNode?): ViewNode {
      val qualifiedName = "${stringTable[view.packageName]}.${stringTable[view.className]}"
      val viewId = stringTable[view.viewId]
      val textValue = stringTable[view.textValue]
      val layout = stringTable[view.layout]
      val x = view.x + (parent?.x ?: 0)
      val y = view.y + (parent?.y ?: 0)
      val node = ViewNode(view.drawId, qualifiedName, layout, x, y, view.width, view.height, viewId, textValue)
      view.subViewList.map { loadView(it, node) }.forEach { node.children[it.drawId] = it }
      return node
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelModified(old: ViewNode?, new: ViewNode?, structuralChange: Boolean) {
    if (structuralChange) {
      layoutInspector?.let { inspector ->
        tree.model = DefaultTreeModel(MyTreeNode(inspector.layoutInspectorModel.root, null))
      }
    }
  }

  private fun modelChanged(old: InspectorModel, new: InspectorModel) {
    tree.model = DefaultTreeModel(MyTreeNode(new.root, null))
    old.selectionListeners.remove(this::selectionChanged)
    new.selectionListeners.add(this::selectionChanged)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun selectionChanged(old: ViewNode?, new: ViewNode?) {
    if (new == null) {
      tree.clearSelection()
      return
    }
    tree.selectionPath = (tree.model.root as MyTreeNode).findPath(new)
  }

  private class MyTreeNode(val root: ViewNode, val _parent: MyTreeNode?) : TreeNode {
    private val _children = root.children.values.map { MyTreeNode(it, this) }

    override fun children(): Enumeration<*> {
      return _children.toEnumeration()
    }

    override fun isLeaf() = root.children.isEmpty()

    override fun getChildCount() = root.children.size

    override fun getParent() = _parent

    override fun getChildAt(childIndex: Int) = _children[childIndex]

    override fun getIndex(node: TreeNode?) = _children.indexOf(node)

    override fun getAllowsChildren() = true

    override fun toString() = "${root.drawId}: ${root.qualifiedName.substringAfterLast('.')}"

    fun findPath(target: ViewNode) : TreePath? {
      val nodes = mutableListOf<MyTreeNode>()
      if (findPathInternal(target, nodes)) {
        return TreePath(nodes.reversed().toTypedArray())
      }
      return null
    }

    fun findPathInternal(target: ViewNode, collector: MutableList<MyTreeNode>): Boolean {
      if (root == target) {
        collector.add(this)
        return true
      }
      for (child in _children) {
        if (child.findPathInternal(target, collector)) {
          collector.add(this)
          return true
        }
      }
      return false
    }
  }
}

private fun <T> List<T>.toEnumeration(): Enumeration<T> {
  return object : Enumeration<T> {
    var count = 0

    override fun hasMoreElements(): Boolean {
      return count < size
    }

    override fun nextElement(): T {
      if (count < size) {
        return get(count++)
      }
      throw IndexOutOfBoundsException("$count >= $size")
    }
  }
}
