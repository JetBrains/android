/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.chart

import com.android.tools.adtui.model.HNode
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet
import com.android.tools.profilers.memory.chart.MemoryVisualizationModel.XAxisFilter
import java.util.Comparator

/**
 * This class wraps a [ClassifierSet] exposing attributes needed to render a call chart. The [HNode] is the model for an
 * HRenderer that when combined determine the layout and style for the HTreeChart.
 */
class ClassifierSetHNode(private val callChartModel: MemoryVisualizationModel,
                         val data: ClassifierSet,
                         private val depth: Int) : HNode<ClassifierSetHNode> {
  /**
   * Not all ClassifierSets have a start / end time. As such a sorted listed of nodes is needed. This list is sorted by the duration
   * and is used to compute the start time offset required by the HTreeChart to determine the rendering order.
   */
  private val children = data.childrenClassifierSets.map { ClassifierSetHNode(callChartModel, it, depth + 1) }.toSortedSet(
    Comparator.comparingLong { obj: ClassifierSetHNode -> obj.duration }.reversed()).toList()
  private var startOffset: Long = 0

  /**
   * Enumerate all children and update the start offset for each child.
   * The myChildren list is expected to be sorted by duration. The start offset of each child is equal to the sum of the durations of all
   * previous children. This allows the HTreeChart to render largest elements on the left and smallest on the right.
   */
  fun updateChildrenOffsets() {
    var childOffset = startOffset
    for (child in children) {
      child.startOffset = childOffset
      childOffset += child.duration
      child.updateChildrenOffsets()
    }
  }

  /**
   * Calculate the size of the HNode based on the requested filter from the model.
   * @param data object to query
   * @return number that represents this objects size/length for the specified filter.
   */
  private fun computeDuration(data: ClassifierSet): Long {
    return when (callChartModel.axisFilter) {
      XAxisFilter.TOTAL_COUNT -> data.totalObjectCount.toLong()
      XAxisFilter.TOTAL_SIZE -> data.totalShallowSize
      XAxisFilter.ALLOC_SIZE -> data.allocationSize
      XAxisFilter.ALLOC_COUNT -> data.deltaAllocationCount.toLong()
    }
  }

  override fun getChildCount(): Int {
    return children.size
  }

  override fun getChildAt(index: Int): ClassifierSetHNode {
    return children[index]
  }

  override fun getParent(): ClassifierSetHNode? {
    return null
  }

  override fun getStart(): Long {
    return startOffset
  }

  override fun getEnd(): Long {
    return start + computeDuration(data)
  }

  override fun getDepth(): Int {
    return depth
  }

  override fun getDuration(): Long {
    return computeDuration(data)
  }

  val isMatched: Boolean
    get() = data.isMatched

  val isFiltered: Boolean
    get() = data.isFiltered

  override fun getFirstChild(): ClassifierSetHNode? {
    return children.first()
  }

  override fun getLastChild(): ClassifierSetHNode? {
    return children.last()
  }

  val name: String
    get() = data.name
}