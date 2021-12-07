/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.android.tools.componenttree.api.BadgeItem
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.NodeType
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.containers.ContainerUtil
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath
import kotlin.properties.Delegates

/** Type safe access to generic accessor */
private fun <T> NodeType<T>.parentOf(item: Any): Any? {
  return parentOf(clazz.cast(item))
}

/** Type safe access to generic accessor */
private fun <T> NodeType<T>.childrenOf(item: Any): List<*> {
  return childrenOf(clazz.cast(item))
}

/** Type safe access to generic accessor */
private fun <T> NodeType<T>.toSearchString(item: Any): String {
  return toSearchString(clazz.cast(item))
}

/**
 * Implementation of the tree model specified in the [com.android.tools.componenttree.api.ComponentTreeBuilder].
 */
class TreeTableModelImpl(
  val badgeItems: List<BadgeItem>,
  private val nodeTypeLookupMap: Map<Class<*>, NodeType<*>>,
  private val invokeLater: (Runnable) -> Unit
) : ComponentTreeModel, TreeTableModel, BaseTreeModel<Any>() {
  private val rendererCache: MutableMap<Class<*>, TreeCellRenderer> = mutableMapOf()
  private val modelListeners: MutableList<TreeModelListener> = ContainerUtil.createConcurrentList()

// region ComponentTreeModel implementation
  override var treeRoot: Any? by Delegates.observable(null) { _, _, newRoot -> hierarchyChanged(newRoot) }

  override fun hierarchyChanged(changedNode: Any?) {
    invokeLater.invoke(Runnable { fireTreeChange(changedNode) })
  }
// endregion

// region TreeTableModel implementation
  override fun getRoot() = treeRoot

  override fun getChildren(parent: Any): List<*> = children(parent)

  override fun valueForPathChanged(path: TreePath?, newValue: Any?) { }

  override fun addTreeModelListener(listener: TreeModelListener) {
    modelListeners.add(listener)
  }

  override fun removeTreeModelListener(listener: TreeModelListener) {
    modelListeners.remove(listener)
  }

  override fun getColumnCount(): Int = 1 + badgeItems.size

  override fun getColumnName(column: Int): String = when(column) {
    0 -> "Tree"
    else -> "Badge$column"
  }

  override fun getColumnClass(column: Int): Class<*> = when(column) {
    0 -> com.intellij.ui.treeStructure.treetable.TreeTableModel::class.java
    else -> Icon::class.java
  }

  override fun getValueAt(node: Any?, column: Int): Any? = node

  override fun isCellEditable(node: Any?, column: Int): Boolean = false

  override fun setValueAt(aValue: Any?, node: Any?, column: Int) {}

  override fun setTree(tree: JTree?) {}
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
  fun children(node: Any): List<*> {
    return typeOf(node).childrenOf(node)
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
  fun rendererOf(node: Any): TreeCellRenderer {
    val type = typeOf(node)
    return rendererCache.getOrPut(type.clazz) { type.createRenderer() }
  }

  /**
   * Clear the renderer cache.
   */
  fun clearRendererCache() {
    rendererCache.clear()
  }

  /**
   * Lookup the [NodeType] of a specified [node].
   */
  private fun typeOf(node: Any): NodeType<*> {
    val entry = nodeTypeLookupMap.entries.firstOrNull { (clazz, _) -> clazz.isInstance(node) } ?: throw RuntimeException(
      "Please add a NodeType for ${node::class.java.name}")
    return entry.value
  }

  /**
   * Compute the depth of the specified [node]
   *
   * Do not use Tree.getPathForRow(row) since it may return null on first draw.
   */
  fun computeDepth(node: Any): Int = generateSequence(node) { parent(it) }.count()

  private fun fireTreeChange(changedNode: Any?) {
    val path = changedNode?.let { TreePath(changedNode) }
    val event = TreeModelEvent(this, path)
    modelListeners.forEach { (it as? TreeTableModelImplListener)?.treeChanged(event) }
  }

  fun fireTreeStructureChange(newRoot: Any?) {
    val path = newRoot?.let { TreePath(newRoot) }
    val event = TreeModelEvent(this, path)
    modelListeners.forEach { it.treeStructureChanged(event) }
  }

}