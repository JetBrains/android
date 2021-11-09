/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture

data class JankAnalysisModel(val event: AndroidFrameTimelineEvent, val capture: SystemTraceCpuCapture): CpuAnalyzable<JankAnalysisModel> {

  override fun getAnalysisModel() =
    CpuAnalysisModel<JankAnalysisModel>("Frame ${event.surfaceFrameToken}").also { model ->
      val eventRange = Range(event.expectedStartUs.toDouble(), event.actualEndUs.toDouble())
      val nodes = capture.captureNodes.filter { eventRange.intersectsWith(it.startGlobal.toDouble(), it.endGlobal.toDouble()) }
      fun chart(type: CpuAnalysisTabModel.Type) =
        CpuAnalysisChartModel<JankAnalysisModel>(type, eventRange, capture, { nodes }).also {
          it.dataSeries.add(this)
        }

      model.addTabModel(Summary(event, capture).also { it.dataSeries.add(this) })
      model.addTabModel(chart(CpuAnalysisTabModel.Type.TOP_DOWN))
      model.addTabModel(chart(CpuAnalysisTabModel.Type.FLAME_CHART))
      model.addTabModel(chart(CpuAnalysisTabModel.Type.BOTTOM_UP))
    }

  class Summary(val event: AndroidFrameTimelineEvent, val capture: CpuCapture)
        : CpuAnalysisSummaryTabModel<JankAnalysisModel>(capture.range) {
    val mainThreadId = firstThreadId { it.isMainThread }
    val gpuThreadId = firstThreadId { it.isGpuThread }
    val renderThreadId = firstThreadId { it.isRenderThread }
    val eventRange = Range(event.expectedStartUs.toDouble(), event.actualEndUs.toDouble())
    override fun getLabel() = "Janky Frame"
    override fun getSelectionRange() = eventRange

    fun getThreadChildren(threadId: Int): List<CaptureNode> =
      capture.getCaptureNode(threadId)?.let(::getRelevantChildren) ?: listOf()

    fun getThreadState(threadId: Int) = capture.systemTraceData!!.getThreadStatesForThread(threadId)

    private fun firstThreadId(isWanted: (CpuThreadInfo) -> Boolean) = capture.threads.first(isWanted).id

    private fun getRelevantChildren(threadNode: CaptureNode) = mutableListOf<CaptureNode>().also { res ->
      fun visit(node: CaptureNode): Unit = node.children.forEach {
        if (Range.intersects(event.expectedStartUs, event.actualEndUs, it.startGlobal, it.endGlobal)) {
          res.add(it)
          visit(it)
        }
      }
      visit(threadNode)
    }
  }
}