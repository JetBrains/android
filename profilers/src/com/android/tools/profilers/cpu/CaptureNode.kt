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
import java.util.PriorityQueue
import java.util.function.Predicate
import java.util.stream.Stream

open class CaptureNode(val data: CaptureNodeModel) : HNode<CaptureNode> {
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
  var clockType = ClockType.GLOBAL

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
   * @param k          number of results
   * @param filter     keep only nodes that satisfies this filter
   * @param comparator to compare nodes by
   * @return up to top k nodes from all descendants, in descending order
   */
  fun getTopKNodes(k: Int, filter: Predicate<CaptureNode>, comparator: Comparator<CaptureNode>): List<CaptureNode> {
    // Put all matched nodes in a priority queue capped at size n, so the queue always contain the n longest running ones.
    val candidates = PriorityQueue(k + 1, comparator)
    descendantsStream.filter(filter).forEach { node ->
      candidates.offer(node)
      if (candidates.size > k) candidates.poll()
    }
    return candidates.sortedWith(comparator.reversed())
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