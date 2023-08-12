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
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.RenderSequence
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture

data class JankAnalysisModel(val event: AndroidFrameTimelineEvent,
                             val capture: SystemTraceCpuCapture,
                             private val runModelUpdate: (Runnable) -> Unit): CpuAnalyzable<JankAnalysisModel> {

  override fun getAnalysisModel() =
    CpuAnalysisModel<JankAnalysisModel>("Frame ${event.surfaceFrameToken}").also { model ->
      val eventRange = Range(event.expectedStartUs.toDouble(), event.actualEndUs.toDouble())
      val nodes = capture.captureNodes.filter { eventRange.intersectsWith(it.startGlobal.toDouble(), it.endGlobal.toDouble()) }
      fun chart(type: CpuAnalysisTabModel.Type) =
        CpuAnalysisChartModel<JankAnalysisModel>(type, eventRange, capture, { nodes }, runModelUpdate).also {
          it.dataSeries.add(this)
        }

      model.addTabModel(Summary(event, capture).also { it.dataSeries.add(this) })
      model.addTabModel(chart(CpuAnalysisTabModel.Type.TOP_DOWN))
      model.addTabModel(chart(CpuAnalysisTabModel.Type.FLAME_CHART))
      model.addTabModel(chart(CpuAnalysisTabModel.Type.BOTTOM_UP))
    }

  class Summary(val event: AndroidFrameTimelineEvent, val capture: SystemTraceCpuCapture, val sequence: RenderSequence)
        : CpuAnalysisSummaryTabModel<JankAnalysisModel>(capture.range) {
    constructor(event: AndroidFrameTimelineEvent, capture: SystemTraceCpuCapture):
      this(event, capture, capture.frameRenderSequence(event))
    private val eventRange = Range(event.expectedStartUs.toDouble(), event.actualEndUs.toDouble())
    override fun getLabel() = "Janky Frame"
    override fun getSelectionRange() = eventRange
    fun getThreadState(threadId: Int) = capture.getThreadStatesForThread(threadId)
  }
}