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
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel

abstract class CpuTreeNode<T : CpuTreeNode<T>?>(val id: String) {
  /**
   * References to [CaptureNode] that are used to extract information from to represent this CpuTreeNode,
   * such as [.getGlobalTotal], [.getGlobalChildrenTotal], etc...
   */
  var total = 0.0
    protected set
  var childrenTotal = 0.0
    protected set

  abstract val nodes: List<CaptureNode>
  abstract val children: List<T>

  abstract val methodModel: CaptureNodeModel
  abstract val filterType: CaptureNode.FilterType
  val isUnmatched: Boolean get() = filterType === CaptureNode.FilterType.UNMATCH

  val self: Double get() = total - childrenTotal

  open fun update(clockType: ClockType, range: Range) {
    reset()
    for (node in nodes) {
      total += getIntersection(range, node, clockType)
      for (child in node.children) {
        childrenTotal += getIntersection(range, child, clockType)
      }
    }
  }

  fun inRange(range: Range): Boolean = nodes.any { it.start < range.max && range.min < it.end }

  fun reset() {
    total = 0.0
    childrenTotal = 0.0
  }

  companion object {
    @JvmStatic
    protected fun getIntersection(range: Range, node: CaptureNode, type: ClockType): Double = when (type) {
      ClockType.GLOBAL -> range.getIntersectionLength(node.startGlobal.toDouble(), node.endGlobal.toDouble())
      ClockType.THREAD -> range.getIntersectionLength(node.startThread.toDouble(), node.endThread.toDouble())
    }

    /**
     * Return a pair of fresh mutable maps indexed by booleans
     */
    internal fun<K, V> mapPair(): (Boolean) -> MutableMap<K, V> {
      val onT = hashMapOf<K, V>()
      val onF = hashMapOf<K, V>()
      return { if (it) onT else onF }
    }
  }
}