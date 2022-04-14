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

import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import java.util.Stack

/**
 * Represents a bottom-up node in the bottom-view. To create a new bottom-up graph
 * at a {@link CaptureNode}, see {@link BottomUpNode.rootAt(CaptureNode)}
 */
sealed class BottomUpNode private constructor(id: String) : CpuTreeNode<BottomUpNode>(id) {

  abstract fun buildChildren(): Boolean

  override fun update(clockType: ClockType, range: Range) {
    // how much time was spent in this call stack path, and in the functions it called
    globalTotal = 0.0
    threadTotal = 0.0

    // The node that is at the top of the call stack, e.g if the call stack looks like B [0..30] -> B [1..20],
    // then the second method can't be outerSoFarByParent.
    // It's used to exclude nodes which aren't at the top of the
    // call stack from the total time calculation.
    // When multiple threads with the same ID are selected, the nodes are merged. When this happens nodes may be interlaced between
    // each of the threads. As such we keep a mapping of outer so far by parents to keep the book keeping done properly.
    val outerSoFarByParent = HashMap<CaptureNode, CaptureNode>()

    // how much time was spent doing work directly in this call stack path
    var self = 0.0
    // myNodes is sorted by CaptureNode#getStart() in increasing order,
    // if they are equal then ancestor comes first
    for (node in nodes) {
      // We use the root node to distinguish if two nodes share the same tree. In the event of multi-select we want to compute the bottom
      // up calculation independently for each tree then sum them after the fact.
      // TODO(153306735): Cache the root calculation, otherwise our update algorithm is going to be O(n*depth) instead of O(n)
      val root = node.findRootNode()
      val outerSoFar = outerSoFarByParent[root]
      if (outerSoFar == null || node.end > outerSoFar.end) {
        if (outerSoFar != null) {
          // |outerSoFarByParent| is at the top of the call stack
          globalTotal += getIntersection(range, outerSoFar, clockType)
          threadTotal += getIntersection(range, outerSoFar, clockType)
        }
        outerSoFarByParent[root] = node
      }
      self += getIntersection(range, node, clockType) -
              node.children.sumOf { getIntersection(range, it, clockType) }
    }
    for (outerSoFar in outerSoFarByParent.values) {
      // |outerSoFarByParent| is at the top of the call stack
      globalTotal += getIntersection(range, outerSoFar, clockType)
      threadTotal += getIntersection(range, outerSoFar, clockType)
    }
    globalChildrenTotal = globalTotal - self
    threadChildrenTotal = threadTotal - self
  }

  private class Root(node: CaptureNode): BottomUpNode("Root") {
    override val methodModel = SingleNameModel("") // sample entry for the root.
    override val filterType get() = CaptureNode.FilterType.MATCH
    override fun buildChildren() = false
    init {
      addChildren(node.preOrderTraversal().map { it to it })
      addNode(node)
      children.forEach(BottomUpNode::buildChildren)
    }
  }

  private class Child(id: String): BottomUpNode(id) {
    private val pathNodes = mutableListOf<CaptureNode>()
    private var childrenBuilt = false

    override val methodModel get() = pathNodes[0].data
    override val filterType get() = pathNodes[0].filterType

    override fun buildChildren(): Boolean = when {
      childrenBuilt -> false
      else -> true.also {
        assert(pathNodes.size == nodes.size)
        addChildren((pathNodes.asSequence() zip nodes.asSequence())
                      .mapNotNull { (pathNode, node) -> pathNode.parent?.let { it to node } })
        childrenBuilt = true
      }
    }

    fun addPathNode(node: CaptureNode) = pathNodes.add(node)
  }

  protected fun addChildren(pathNodesAndNodes: Sequence<Pair<CaptureNode, CaptureNode>>) {
    // We use a separate map for unmatched children, because we can not merge unmatched with matched,
    // i.e all merged children should have the same {@link CaptureNode.FilterType};
    val childrenMap = mapPair<String, Child>()
    pathNodesAndNodes.forEach { (pathNode, node) ->
      val id = pathNode.data.id
      val child = childrenMap(pathNode.isUnmatched).getOrPut(id) { Child(id).also(::addChild) }
      child.addPathNode(pathNode)
      child.addNode(node)
    }
  }

  companion object {
    @JvmStatic fun rootAt(node: CaptureNode): BottomUpNode = Root(node)
  }
}

private fun CaptureNode.preOrderTraversal() = sequence<CaptureNode> {
  val stack = Stack<CaptureNode>().also { it.add(this@preOrderTraversal) }
  while (stack.isNotEmpty()) {
    val next = stack.pop()
    stack.addAll(next.children.asReversed()) // reversed order so that first child is processed first
    // If we don't have an Id then we exclude this node from being added as a child to the parent.
    // The only known occurrence of this is the empty root node used to aggregate multiple selected objects.
    if (next.data.id.isNotEmpty()) yield(next)
  }
}