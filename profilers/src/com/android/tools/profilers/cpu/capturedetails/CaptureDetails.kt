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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.Range
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.VisualNodeCaptureNode
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import kotlin.math.max

sealed class CaptureDetails(val clockType: ClockType, val capture: CpuCapture) {
  abstract val type: Type

  sealed class ChartDetails(clockType: ClockType, cpuCapture: CpuCapture): CaptureDetails(clockType, cpuCapture) {
    abstract val node: CaptureNode?
  }

  /**
   * Helper class for common behavior of Top-Down and Bottom-Up
   */
  sealed class Aggregate<N: CpuTreeNode<N>, M: CpuTreeModel<N>>(clockType: ClockType,
                                                                range: Range,
                                                                nodes: List<CaptureNode>,
                                                                cpuCapture: CpuCapture,
                                                                rootNode: (CaptureNode) -> N,
                                                                model: (ClockType, Range, N) -> M)
    : CaptureDetails(clockType, cpuCapture) {
    val model: M? = when {
      nodes.isEmpty() -> null
      else -> {
        val visual = VisualNodeCaptureNode(SingleNameModel(""), clockType).apply {
          nodes.forEach(::addChild)
          startGlobal = nodes.minOf(CaptureNode::startGlobal)
          endGlobal = nodes.maxOf(CaptureNode::endGlobal)
          startThread = nodes.minOf(CaptureNode::startThread)
          endThread = nodes.maxOf(CaptureNode::endThread)
        }
        model(clockType, range, rootNode(visual))
      }
    }
  }

  class TopDown internal constructor(clockType: ClockType, range: Range, nodes: List<CaptureNode>, cpuCapture: CpuCapture)
    : Aggregate<TopDownNode, TopDownTreeModel>(clockType, range, nodes, cpuCapture, ::TopDownNode, ::TopDownTreeModel) {
    override val type get() = Type.TOP_DOWN
  }

  class BottomUp internal constructor(clockType: ClockType, range: Range, nodes: List<CaptureNode>, cpuCapture: CpuCapture)
    : Aggregate<BottomUpNode, BottomUpTreeModel>(clockType, range, nodes, cpuCapture,
                                                 { BottomUpNode.rootAt(it).apply { update(clockType, range) } },
                                                 ::BottomUpTreeModel) {
    override val type get() = Type.BOTTOM_UP
  }

  class CallChart(clockType: ClockType, val range: Range, nodes: List<CaptureNode>, cpuCapture: CpuCapture)
    : ChartDetails(clockType, cpuCapture) {
    override val node = nodes.firstOrNull()
    override val type get() = Type.CALL_CHART
  }

  class FlameChart internal constructor(clockType: ClockType,
                                        private val selectionRange: Range,
                                        captureNodes: List<CaptureNode>,
                                        cpuCapture: CpuCapture)
    : ChartDetails(clockType, cpuCapture) {
    override val type get() = Type.FLAME_CHART

    val range: Range = Range()
    override var node: CaptureNode? = null
      private set
    val aspect: AspectModel<Aspect> = AspectModel()

    init {
      if (captureNodes.isNotEmpty()) {
        val captureNodes = captureNodes.sortedWith(Comparator.comparingLong(CaptureNode::startGlobal))

        val visual = VisualNodeCaptureNode(SingleNameModel(""), clockType)
        captureNodes.forEach(visual::addChild)
        // This needs to be the start of the earliest node to have an accurate range for multi-selected items.
        visual.startGlobal = captureNodes[0].startGlobal
        visual.startThread = captureNodes[0].startThread

        val topDownNode = TopDownNode(visual).apply {
          update(clockType, Range(0.0, Double.MAX_VALUE)) // to compute the total children time
        }

        // This gets mapped to the sum of all children. This assumes that this node has 0 self time,
        // which is true because we create it.
        // We map to the sum of all children because when multiple nodes are selected, nodes with the same Id are merged.
        // When they are merged, the sum of time is less than or equal to the total time of each node. We need the time to
        // be accurate as when we compute the capture space to screen space, calculations for the graph we need to know what
        // 100% is.
        visual.endGlobal = visual.startGlobal + topDownNode.globalChildrenTotal.toLong()
        visual.endThread = visual.startThread + topDownNode.threadChildrenTotal.toLong()

        fun selectionRangeChanged() {
          // This range needs to account for the multiple children,
          // does it need to account for the merged children?
          topDownNode.update(clockType, selectionRange)
          node = when {
            // If the new selection range intersects the root node, we should reconstruct the flame chart node.
            topDownNode.getTotal(clockType) > 0 -> {
              val start = max(topDownNode.nodes[0].start.toDouble(), selectionRange.min)
              val newNode = convertToFlameChart(topDownNode, start, 0)
              // The intersection check (root.getTotal() > 0) may be a false positive because the root's global total is the
              // sum of all its children for the purpose of mapping a multi-node tree to flame chart space. Thus we need to look at
              // its children to find out the actual intersection.
              when (newNode.lastChild) {
                // No child intersects the selection range, so effectively there's no intersection at all.
                null -> null
                else -> newNode.apply {
                  // At least one child intersects the selection rage, use the last child as the real intersection end.
                  endGlobal = lastChild!!.endGlobal
                  // This is the range used by the HTreeChart to determine if a node is in the range or out of the range.
                  // Because the node is already filtered to the selection we can use the length of the node.
                  range.set(start, end.toDouble())
                }
              }
            }
            // Otherwise, clear the flame chart node and show an empty chart
            else -> null
          }
          aspect.changed(Aspect.NODE)
        }
        selectionRange.addDependency(aspect).onChange(Range.Aspect.RANGE, ::selectionRangeChanged)
        selectionRangeChanged()
      }
    }

    /**
     * Produces a flame chart that is similar to [CallChart], but the identical methods with the same sequence of callers
     * are combined into one wider bar. It converts it from [TopDownNode] as it's similar to FlameChart.
     */
    private fun convertToFlameChart(topDown: TopDownNode, start: Double, depth: Int): CaptureNode =
      CaptureNode(topDown.nodes[0].data, clockType).apply {
        assert(topDown.getTotal(clockType) > 0)

        filterType = topDown.nodes[0].filterType
        startGlobal = start.toLong()
        startThread = start.toLong()
        endGlobal = (start + topDown.globalTotal).toLong()
        endThread = (start + topDown.threadTotal).toLong()
        this.depth = depth

        topDown.children.asSequence()
          .filter {
            it.update(clockType, selectionRange)
            it.getTotal(clockType) > 0
          }
          .sortedWith(compareBy(TopDownNode::isUnmatched).thenComparingDouble { -it.getTotal(clockType) })
          .fold(start) { accStart, child ->
            addChild(convertToFlameChart(child, accStart, depth + 1))
            accStart + child.getTotal(clockType)
          }
      }

    enum class Aspect {
      /**
       * When the root changes.
       */
      NODE
    }
  }

  enum class Type(val build: (ClockType, Range, List<CaptureNode>, CpuCapture) -> CaptureDetails) {
    TOP_DOWN(::TopDown),
    BOTTOM_UP(::BottomUp),
    CALL_CHART(::CallChart),
    FLAME_CHART(::FlameChart)
  }
}