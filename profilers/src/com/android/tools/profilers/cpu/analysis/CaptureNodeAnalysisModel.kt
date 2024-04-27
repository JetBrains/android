/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import java.util.stream.Collectors
import kotlin.streams.asSequence
import kotlin.streams.toList

data class CaptureNodeAnalysisModel constructor(
  val node: CaptureNode,
  private val capture: CpuCapture,
  private val runModelUpdate: (Runnable) -> Unit,
  private val nameToNodes: Map<String, List<CaptureNode>> = mapOf()
) : CpuAnalyzable<CaptureNodeAnalysisModel> {
  val nodeRange: Range get() = Range(node.start.toDouble(), node.end.toDouble())

  /**
   * Statistics of all occurrences of this node, e.g. count, min, max.
   *
   * Uses pre-computed name to nodes mapping entry to skip the name filtering if present.
   */
  val allOccurrenceStats: CaptureNodeAnalysisStats
    get() {
      val nodeName = node.data.fullName
      val allOccurrences =
        // Try to get all occurrences from lookup table
        if(nameToNodes.contains(nodeName)) {
          nameToNodes[nodeName]!!
        }
        // Otherwise, compute all node occurrences via stream
        else {
          node.findRootNode().descendantsStream.parallel().filter(::matchesFullName).collect(Collectors.toList())
        }
      // Perform node analysis and compute stats
      return CaptureNodeAnalysisStats.fromNodes(allOccurrences)
    }

  /**
   * @return top k nodes by duration, with same full name, in descending order.
   */
  fun getLongestRunningOccurrences(k: Int): List<CaptureNode> =
    node.findRootNode().getTopKNodes(k, node.data.fullName, compareBy(CaptureNode::getDuration), nameToNodes)

  private fun matchesFullName(otherNode: CaptureNode) = node.data.fullName == otherNode.data.fullName

  override fun getAnalysisModel(): CpuAnalysisModel<CaptureNodeAnalysisModel> {
    val nodeRange = nodeRange
    val nodes = setOf(node)
    return CpuAnalysisModel<CaptureNodeAnalysisModel>(node.data.nameWithSuffix, "%d events").also { model ->
      fun add(tab: CpuAnalysisTabModel<CaptureNodeAnalysisModel>) {
        tab.dataSeries.add(this)
        model.addTabModel(tab)
      }

      // Summary
      add(CaptureNodeAnalysisSummaryTabModel(capture.range, capture.type))

      // Flame Chart
      add(CpuAnalysisChartModel(CpuAnalysisTabModel.Type.FLAME_CHART, nodeRange, capture, { nodes }, runModelUpdate))

      // Top Down
      add(CpuAnalysisChartModel(CpuAnalysisTabModel.Type.TOP_DOWN, nodeRange, capture, { nodes }, runModelUpdate))

      // Bottom Up
      add(CpuAnalysisChartModel(CpuAnalysisTabModel.Type.BOTTOM_UP, nodeRange, capture, { nodes }, runModelUpdate))

      // Events
      add(CaptureNodeAnalysisEventsTabModel(capture.range))
    }
  }
}