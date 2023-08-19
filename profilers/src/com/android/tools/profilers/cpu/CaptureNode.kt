/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.HNode
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.adtui.model.filter.FilterResult
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.google.common.annotations.VisibleForTesting
import java.util.PriorityQueue
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.reflect.KMutableProperty1

open class CaptureNode(val data: CaptureNodeModel, var clockType: ClockType) : HNode<CaptureNode> {

  @VisibleForTesting
  constructor(data: CaptureNodeModel) : this(data, ClockType.GLOBAL) {}

  /**
   * Start time with GLOBAL clock.
   */
  var startGlobal = 0L

  /**
   * End time with GLOBAL clock.
   */
  var endGlobal = 0L

  /**
   * Start time with THREAD clock.
   */
  var startThread = 0L

  /**
   * End time with THREAD clock.
   */
  var endThread = 0L
  @JvmField
  protected val childrenList = mutableListOf<CaptureNode>()

  /**
   * The parent of its child is set to it when it is added [.addChild]
   */
  private var parent: CaptureNode? = null

  /**
   * see [FilterType].
   */
  var filterType = FilterType.MATCH

  /**
   * The shortest distance from the root.
   */
  private var depth = 0

  /**
   * Aspect model for the node [Aspect]. Only root nodes provide aspect changes so it is lazily initialized to avoid the overhead of
   * its instantiation.
   */
  private var aspectModelPlaceHolder: AspectModel<Aspect>? = null

  val children: List<CaptureNode>
    get() = childrenList

  val isUnmatched: Boolean
    get() = filterType == FilterType.UNMATCH

  /**
   * @return all descendants in pre-order (i.e. node, left, right) as a stream.
   */
  val descendantsStream: Stream<CaptureNode>
    get() = Stream.concat(Stream.of(this), children.stream().flatMap { it.descendantsStream })

  open fun addChild(node: CaptureNode) {
    childrenList.add(node)
    node.parent = this
  }

  fun addChildren(nodes: Collection<CaptureNode>) = nodes.forEach(::addChild)
  fun clearChildren() = childrenList.clear()

  override fun getChildCount() = childrenList.size
  override fun getChildAt(index: Int) = childrenList[index]
  override fun getParent() = parent

  /**
   * @return root node of this node. If this node doesn't have a parent, return this node.
   */
  fun findRootNode(): CaptureNode {
    tailrec fun find(node: CaptureNode): CaptureNode = when (val parent = node.parent) {
      null -> node
      else -> find(parent)
    }
    return find(this)
  }

  override fun getStart() = if (clockType == ClockType.THREAD) startThread else startGlobal
  override fun getEnd() = if (clockType == ClockType.THREAD) endThread else endGlobal
  override fun getDepth() = depth

  val aspectModel: AspectModel<Aspect>
    get() {
      if (aspectModelPlaceHolder == null) {
        aspectModelPlaceHolder = AspectModel()
      }
      return aspectModelPlaceHolder!!
    }

  /**
   * Returns the proportion of time the method was using CPU relative to the total (wall-clock) time that passed.
   */
  fun threadGlobalRatio(): Double = (endThread - startThread).toDouble() / (endGlobal - startGlobal)

  fun setDepth(depth: Int) {
    this.depth = depth
  }

  /**
   * Iterate through all descendants of this node, apply a filter and then find the top k nodes by the given comparator.
   *
   * Uses pre-computed name to nodes mapping entry to skip the name filtering if present.
   *
   * @param k            number of results
   * @param nodeName     name of node to be analyzed
   * @param filter       keep only nodes that satisfies this filter
   * @param comparator   to compare nodes by
   * @param nameToNodes  mapping of node names to a list of nodes with the matching name
   * @return up to top k nodes from all descendants, in descending order
   */
  fun getTopKNodes(
    k: Int,
    nodeName: String,
    comparator: Comparator<CaptureNode>,
    nameToNodes: Map<String, List<CaptureNode>>
  ): List<CaptureNode> {
    val nameMatchedNodesStream =
      // Try to get matched names from lookup table
      if (nameToNodes.contains(nodeName))
        nameToNodes[nodeName]!!.stream()
      // Otherwise, compute the stream using a filter
      else
        descendantsStream
          .parallel()
          .filter { it.data.fullName == nodeName }

    // Get the top K longest duration nodes with the matching name
    return nameMatchedNodesStream
      .sorted(comparator.reversed())
      .limit(k.toLong())
      .collect(Collectors.toList())
  }

  /**
   * Apply a filter to this node and its children.
   *
   * @param filter filter to apply. An empty matches all nodes.
   * @return filter result, e.g. number of matches.
   */
  fun applyFilter(filter: Filter) = computeFilter(filter).also { aspectModelPlaceHolder?.changed(Aspect.FILTER_APPLIED) }

  /**
   * Recursively applies filter to this node and its children.
   */
  private fun computeFilter(filter: Filter): FilterResult {
    var totalCount = 0
    var matchCount = 0
    fun CaptureNode.updateFilter(ancestorMatches: Boolean) {
      val nodeExactMatch = filter.matches(data.fullName)
      val matches = ancestorMatches || nodeExactMatch
      if (nodeExactMatch) matchCount++
      totalCount++
      children.forEach { it.updateFilter(matches) }
      filterType = when {
        !matches && children.all { it.isUnmatched } -> FilterType.UNMATCH
        nodeExactMatch && !filter.isEmpty -> FilterType.EXACT_MATCH
        else -> FilterType.MATCH
      }
    }
    updateFilter(false)
    return FilterResult(matchCount, totalCount, !filter.isEmpty)
  }

  private fun resetDepth(n: Int) {
    depth = n
    children.forEach { it.resetDepth(n + 1) }
  }

  /**
   * Return a new tree like this one, but with all uninteresting nodes collapsed into the given abbreviation.
   * In the returned abbreviated tree:
   *   - no abbreviated parent has any abbreviated child of the same kind
   *   - no consecutive siblings are both abbreviated of the same kind
   */
  fun abbreviatedBy(abbreviate: (CaptureNode) -> CaptureNodeModel?, isAbbreviation: (CaptureNodeModel) -> Boolean) =
    fold({it.clonedWithData(abbreviate(it) ?: it.data)}) { clone, abbreviatedChild ->
      clone.also {
        when {
          // Parent and child are both abbreviated -> merge child's children with parent's
          isAbbreviation(clone.data) && abbreviatedChild.data === clone.data -> clone.addChildren(abbreviatedChild.children)
          // Consecutive children are abbreviated -> merge em
          isAbbreviation(abbreviatedChild.data) && clone.children.lastOrNull()?.data === abbreviatedChild.data ->
            clone.children.last().let { mergedChild ->
              mergedChild.addChildren(abbreviatedChild.children)
              mergedChild.copyFrom(abbreviatedChild, CaptureNode::endGlobal, CaptureNode::endThread)
            }
          // Nothing to merge, just add it
          else -> clone.addChild(abbreviatedChild)
        }
      }
    }.also { it.resetDepth(depth) }

  fun abbreviatedBy(shouldAbbreviate: (CaptureNode) -> Boolean, abbreviation: CaptureNodeModel) =
    abbreviatedBy({ node -> abbreviation.takeIf { shouldAbbreviate(node) } }, { it === abbreviation })

  /**
   * Accumulates value of type T over the capture tree.
   * This is a non-standard version of fold that instead of taking a single `combine` function
   * that combines sub-results, mirroring CaptureNode's shape,
   * allows more incremental accumulation of the result.
   *
   * @param init Computes the initial result from the node
   * @param combine Combines the accumulated result with the result from the next child to produce a new one
   */
  fun <T> fold(init: (CaptureNode) -> T, combine: (T, T) -> T): T =
    children.fold(init(this)) { acc, child -> combine(acc, child.fold(init, combine)) }

  /**
   * Return a copy of this node (same start, end, etc.) with custom data and empty children list
   */
  private fun clonedWithData(data: CaptureNodeModel) = CaptureNode(data, clockType).also { clone ->
    clone.copyFrom(this,
                   CaptureNode::startGlobal, CaptureNode::endGlobal,
                   CaptureNode::startThread, CaptureNode::endThread)
  }

  enum class FilterType {
    /**
     * This [CaptureNode] matches to the filter.
     */
    EXACT_MATCH,

    /**
     * Either one of its ancestor is a [.EXACT_MATCH] or one of its descendant.
     */
    MATCH,

    /**
     * Neither this node matches to the filter nor one of its ancestor nor one of its descendant.
     */
    UNMATCH
  }

  enum class Aspect {
    /**
     * Fired when a [Filter] is applied to this node.
     */
    FILTER_APPLIED
  }
}

private fun<T> T.copyFrom(that: T, vararg properties: KMutableProperty1<T, *>) {
  fun<P> T.copy(property: KMutableProperty1<T, P>) = property.set(this, property.get(that))
  properties.forEach { copy(it) }
}
