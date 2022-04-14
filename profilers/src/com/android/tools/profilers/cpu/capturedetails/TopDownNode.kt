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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel

/**
 * A top-down CPU usage tree. This is a node on that tree and represents all the calls that share the same callstack upto a point.
 * It's created from an execution tree by merging the nodes with the same path from the root.
 */
class TopDownNode(node: CaptureNode) : CpuTreeNode<TopDownNode>(node.data.id) {
  override val methodModel get() = nodes[0].data
  override val filterType get() = nodes[0].filterType

  init {
    addNode(node)

    // We're adding unmatched children separately, because we don't want to merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType}.
    addChildren(node, false)
    addChildren(node, true)
  }

  /**
   * Adds children of {@param node} whose filter type matches to the flag {@param unmatched}.
   */
  private fun addChildren(node: CaptureNode, unmatched: Boolean) {
    val children = HashMap<String, TopDownNode>()
    for (child in node.children) {
      if (unmatched == child.isUnmatched) {
        val other = TopDownNode(child)
        when (val prev = children[child.data.id]) {
          null -> {
            children[child.data.id] = other
            addChild(other)
          }
          else -> prev.merge(other)
        }
      }
    }
  }

  private fun merge(other: TopDownNode) {
    addNodes(other.nodes)

    // We use a separate map for unmatched children, because we can not merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType};
    val childrenMap = mapPair<String, TopDownNode>()
    children.forEach { childrenMap(it.isUnmatched)[it.id] = it }
    for (otherChild in other.children) {
      when (val existing = childrenMap(otherChild.isUnmatched)[otherChild.id]) {
        null -> addChild(otherChild)
        else -> existing.merge(otherChild)
      }
    }
  }
}
