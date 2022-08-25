/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.monitor.ui

import com.intellij.openapi.diagnostic.thisLogger
import java.util.Enumeration
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode

object TreeUtil {
  /**
   * Given a parent tree node, with or without children, and a list of entries any type "U",
   * incrementally update the children of the parent tree node so that they exactly match
   * the list of entries. After this method is called, the list of children of the parent node
   * should have the same number of elements as the list of entries, and they all should
   * be equivalent.
   *
   * **Warning:**
   * To make sure this algorithm is bounded at O(N), there is an assumption
   * that both the existing list of children and the list of new entries are sorted
   * with an **equivalent ordering**, as defined by the
   * [UpdateChildrenOps.compareNodesForSorting] method.
   * For example, for a file explorer view, entries would typically be sorted by filenames.
   *
   * @param treeModel The model through which child nodes are added to/removed from the parent
   * @param parentNode The parent node
   * @param newEntries A list of entries of arbitrary type to map into the list of child nodes of the parent
   * @param ops User provided operations required to map and compare tree node with elements of [newEntries]
   * @param <T> The type of the tree nodes, must extend [MutableTreeNode]
   * @param <U> An arbitrary type for the new entries to map to tree nodes.
   * @return The list of tree nodes that have been created.
  </U></T> */
  fun <T : MutableTreeNode, U> updateChildrenNodes(
    treeModel: DefaultTreeModel,
    parentNode: MutableTreeNode,
    newEntries: List<U>,
    ops: UpdateChildrenOps<T, U>
  ): List<T> {
    thisLogger().info("updateChildrenNodes($treeModel, $parentNode, ${newEntries.size} nodes)")
    return if (newEntries.isEmpty()) {
      // Special case: new list is empty, so we remove all children
      // This is more efficient that doing incremental updates
      removeAllChildren(parentNode)
      treeModel.nodeStructureChanged(parentNode)
      emptyList()
    }
    else if (parentNode.childCount == 0) {
      // Special case: no existing children, so we map all entries and add them
      // This is more efficient that doing incremental updates
      val nodes = newEntries.map { entry -> ops.mapEntry(entry) }
      removeAllChildren(parentNode)
      setAllowsChildren(parentNode) // Note: Must be done *before* inserting new nodes
      nodes.forEach { x -> parentNode.insert(x, parentNode.childCount) }
      treeModel.nodeStructureChanged(parentNode)
      nodes
    }
    else {
      // Common case: We go through both list one element at a time, ensuring that the
      // list of children up to the current index is updated to match the new entries
      val addedNodes: MutableList<T> = ArrayList()
      var childIndex = 0
      var childCount = parentNode.childCount
      var newEntryIndex = 0
      val newEntryCount = newEntries.size
      while (newEntryIndex < newEntries.size || childIndex < parentNode.childCount) {
        assert(childIndex == newEntryIndex)

        // We reached the end of the new children, delete existing children from parent
        if (newEntryIndex >= newEntryCount) {
          treeModel.removeNodeFromParent(parentNode.getChildAt(childIndex) as MutableTreeNode)
          childCount--
          continue
        }

        // We reached the end of the existing children, just add a node from the new ones
        if (childIndex >= childCount) {
          val newEntryNode = ops.mapEntry(newEntries[newEntryIndex])
          addedNodes.add(newEntryNode)
          setAllowsChildren(parentNode) // Note: Must be done *before* inserting new nodes
          treeModel.insertNodeInto(newEntryNode, parentNode, childIndex)
          newEntryIndex++
          childIndex++
          childCount++
          continue
        }

        // Both sides have an entry.
        val childNode = ops.getChildNode(parentNode, childIndex)
        if (childNode == null) {
          // Existing tree node is not of type "T", remove it.
          treeModel.removeNodeFromParent(parentNode.getChildAt(childIndex) as MutableTreeNode)
          childCount--
          continue
        }
        val compareResult = ops.compareNodesForSorting(childNode, newEntries[newEntryIndex])
        if (compareResult == 0) {
          // We have the same node identity, render the tree node if needed, and move to next node
          // in both sequences.
          if (!ops.equals(childNode, newEntries[newEntryIndex])) {
            treeModel.nodeChanged(childNode)
          }
          ops.updateNode(childNode, newEntries[newEntryIndex])
          childIndex++
          newEntryIndex++
          continue
        }
        if (compareResult < 0) {
          treeModel.removeNodeFromParent(parentNode.getChildAt(childIndex) as MutableTreeNode)
          childCount--
          continue
        }
        assert(compareResult > 0)
        val nextIndex = findIndexOfNextEntry(newEntries, newEntryIndex + 1, childNode, ops)
        assert(nextIndex >= newEntryIndex + 1)
        assert(nextIndex <= newEntries.size)
        while (newEntryIndex < nextIndex) {
          val newChildNode = ops.mapEntry(newEntries[newEntryIndex])
          addedNodes.add(newChildNode)
          treeModel.insertNodeInto(newChildNode, parentNode, childIndex)
          childIndex++
          childCount++
          newEntryIndex++
        }
      }
      assert(childIndex == newEntryIndex)
      assert(childCount == newEntryCount)
      addedNodes
    }
  }

  /**
   * Return the index of the first entry that comes *after* (or is equal to) the
   * given tree node.
   */
  private fun <T : MutableTreeNode, U> findIndexOfNextEntry(
    entries: List<U>,
    beginIndex: Int,
    treeNode: T,
    ops: UpdateChildrenOps<T, U>
  ): Int {
    for (i in beginIndex until entries.size) {
      if (ops.compareNodesForSorting(treeNode, entries[i]) <= 0) {
        return i
      }
    }
    return entries.size
  }

  private fun removeAllChildren(node: MutableTreeNode) {
    for (i in node.childCount - 1 downTo 0) {
      node.remove(i)
    }
  }

  private fun setAllowsChildren(node: MutableTreeNode) {
    if (node is DefaultMutableTreeNode) {
      node.allowsChildren = true
    }
  }

  /**
   * Define operations for mapping [MutableTreeNode] instances to arbitrary elements of type [U].
   * See [updateChildrenNodes].
   */
  interface UpdateChildrenOps<T : MutableTreeNode, U> {
    /**
     * Returns a child [MutableTreeNode] if it is of type [T], or `null` otherwise.
     */
    fun getChildNode(parentNode: MutableTreeNode, index: Int): T?

    /**
     * Maps an [entry] to a [MutableTreeNode] of type [T].
     */
    fun mapEntry(entry: U): T

    /**
     * Compares a [node] and an [entry], for sorting purposes.
     */
    fun compareNodesForSorting(node: T, entry: U): Int

    /**
     * Returns `true` if [node] and [entry] are equal. This is used by [updateChildrenNodes] to
     * know if a tree node needs to be rendered if it was already present in the tree.
     */
    fun equals(node: T, entry: U): Boolean

    /**
     * (Optionally) update a given TreeNode with a new entry value
     */
    fun updateNode(node: T, entry: U)
  }
}

/**
 * Returns a [Sequence] of [TreeNode] over the children of this [TreeNode].
 */
val TreeNode.childrenSequence: Sequence<TreeNode>
  get() {
  return Sequence {
    val e: Enumeration<*> = children()
    val iterator = object : Iterator<TreeNode> {
      override fun next(): TreeNode {
        return e.nextElement() as TreeNode
      }

      override fun hasNext(): Boolean {
        return e.hasMoreElements()
      }
    }
    iterator
  }
}
