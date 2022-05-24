/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

/**
 * A temporary node structure representing the desired tree structure. Implementing classes
 * must override equals/hashCode methods to implement structural equality support.
 */
interface ShadowNode {
  fun getChildrenModels(): Collection<ShadowNode> = listOf()
  fun createNode(): ShadowedTreeNode
  fun onChange(disposable: Disposable, listener: () -> Unit) = Unit
}

interface ShadowedTreeNode : MutableTreeNode, Disposable {
  val shadowNode: ShadowNode
}
val ShadowedTreeNode.childNodes: Sequence<ShadowedTreeNode> get() = children().asSequence().map { it as ShadowedTreeNode }

/**
 * Initializes [node] which children of [from] and subscribes to change notifications from [from].
 */
internal fun DefaultTreeModel.initializeNode(
  node: ShadowedTreeNode,
  from: ShadowNode
) {
  this.updateChildrenOf(node, from)
  from.onChange(node) { this.updateChildrenOf(node, from = from) }
}

/**
 * Updates [parentNode]'s collection of nodes so that it reflects the children of [from].
 */
private fun DefaultTreeModel.updateChildrenOf(
  parentNode: ShadowedTreeNode,
  from: ShadowNode
) {
  val children = from.getChildrenModels().toSet()
  val existing =
    parentNode
      .childNodes
      .map { it.shadowNode to it }
      .toMap()

  fun removeNodes() {
    existing
      .filterKeys { !children.contains(it) }
      .forEach {
        // Remove any nodes that should no longer be there.
        removeNodeFromParent(it.value)
        Disposer.dispose(it.value)
      }
  }

  children
    .forEachIndexed { index, model ->
      val existingNode = existing[model]
      // We need to remove any not necessary nodes to prevent unnecessary moves of existing nodes which result in them being collapsed.
      // We cannot do it right now because it might result in our parent getting collapsed. We will remove nodes once we are sure that at
      // least one node remains.
      when {
        existingNode != null -> {
          // We need to have at least one node to prevent the parent node from becoming collapsed
          // when the last child is removed at intermediate step. We can do it as the first step because [existingNode] will remain.
          if (index == 0) removeNodes()

          // Move existing nodes to the right positions if require.
          if (getIndexOfChild(parentNode, existingNode) != index) {
            removeNodeFromParent(existingNode)
            insertNodeInto(existingNode, parentNode, index)
          }
        }
        else -> {
          // Create any new nodes and insert them at their positions.
          val newNode = model.createNode()
          Disposer.register(parentNode, newNode)
          insertNodeInto(
            newNode.also { initializeNode(it, from = model) },
            parentNode,
            index)

          // We need to have at least one node to prevent the parent node from becoming collapsed
          // when the last child is removed at intermediate step. It is now safe to do because we inserted a new node.
          if (index == 0) removeNodes()
        }
      }
    }
    // If the target state is "no nodes", then any existing nodes are not yet removed. Remove them here.
    if (children.isEmpty()) removeNodes()
}

