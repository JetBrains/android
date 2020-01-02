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
package com.android.tools.componenttree.impl

import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.NodeType
import com.intellij.util.containers.ContainerUtil
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.properties.Delegates

/** Type safe access to generic accessor */
private fun <T> NodeType<T>.parentOf(item: Any): Any? {
  return parentOf(clazz.cast(item))
}

/** Type safe access to generic accessor */
private fun <T> NodeType<T>.childrenOf(item: Any): List<Any> {
  return childrenOf(clazz.cast(item))
}

/** Type safe access to generic accessor */
private fun <T> NodeType<T>.toSearchString(item: Any): String {
  return toSearchString(clazz.cast(item))
}

/**
 * Implementation of the tree model specified in the [com.android.tools.componenttree.api.ComponentTreeBuilder].
 */
class ComponentTreeModelImpl(
  private val nodeTypeLookupMap: Map<Class<*>, NodeType<*>>,
  private val invokeLater: (Runnable) -> Unit
) : ComponentTreeModel, TreeModel {
  private val rendererCache: MutableMap<Class<*>, TreeCellRenderer> = mutableMapOf()
  private val modelListeners: MutableList<TreeModelListener> = ContainerUtil.createConcurrentList()

  override var treeRoot: Any? by Delegates.observable<Any?>(null) { _, _, newRoot -> hierarchyChanged(newRoot) }

  //region Implementation of TreeModel

  override fun getRoot() = treeRoot

  override fun isLeaf(node: Any?) = children(node).isEmpty()

  override fun getChildCount(parent: Any?) = children(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?) = children(parent).indexOf(child)

  override fun getChild(parent: Any?, index: Int) = children(parent)[index]

  override fun valueForPathChanged(path: TreePath?, newValue: Any?) { }

  override fun addTreeModelListener(listener: TreeModelListener) {
    modelListeners.add(listener)
  }

  override fun removeTreeModelListener(listener: TreeModelListener) {
    modelListeners.remove(listener)
  }

  // endregion

  /**
   * Find the parent node of the given [node] using the relevant [NodeType].
   */
  fun parent(node: Any): Any? {
    return typeOf(node).parentOf(node)
  }

  /**
   * Find the children of the given [node] using the relevant [NodeType].
   */
  fun children(node: Any?): List<Any> {
    return node?.let { typeOf(it).childrenOf(it) } ?: emptyList()
  }

  /**
   * Compute a search string (for SpeedSearch) using the relevant [NodeType] for the specified tree [node].
   */
  fun toSearchString(node: Any?): String {
    return node?.let { typeOf(node).toSearchString(node) } ?: ""
  }

  /**
   * Find the [TreeCellRenderer] using the relevant [NodeType] for the specified [node].
   */
  fun rendererOf(node: Any?): TreeCellRenderer? {
    val type = node?.let { typeOf(node) } ?: return null
    return rendererCache[type.clazz] ?: createRenderer(type)
  }

  /**
   * Clear the renderer cache.
   */
  fun clearRendererCache() {
    rendererCache.clear()
  }

  private fun createRenderer(type: NodeType<*>): TreeCellRenderer {
    val renderer = type.createRenderer()
    rendererCache[type.clazz] = renderer
    return renderer
  }

  /**
   * Lookup the [NodeType] of a specified [node].
   */
  private fun typeOf(node: Any): NodeType<*> {
    val entry = nodeTypeLookupMap.entries.firstOrNull { (clazz, _) -> clazz.isInstance(node) } ?: throw RuntimeException(
      "Please add a NodeType for ${node::class.java.name}")
    return entry.value
  }

  override fun hierarchyChanged(changedNode: Any?) {
    invokeLater.invoke(Runnable { fireTreeChange(changedNode) })
  }

  private fun fireTreeChange(changedNode: Any?) {
    val path = changedNode?.let { TreePath(changedNode) }
    val event = TreeModelEvent(this, path)
    modelListeners.forEach { (it as? ComponentTreeModelListener)?.treeChanged(event) }
  }

  fun fireTreeStructureChange(newRoot: Any?) {
    val path = newRoot?.let { TreePath(newRoot) }
    val event = TreeModelEvent(this, path)
    modelListeners.forEach { it.treeStructureChanged(event) }
  }
}
