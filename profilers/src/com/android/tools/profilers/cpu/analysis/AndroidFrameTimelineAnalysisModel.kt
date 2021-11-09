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

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel.Type
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import java.util.concurrent.TimeUnit

object AndroidFrameTimelineAnalysisModel {
  @JvmStatic
  fun of(capture: CpuCapture): CpuAnalysisModel<CpuCapture>? = when {
    capture.systemTraceData?.getAndroidFrameTimelineEvents()?.isNotEmpty() ?: false -> {
      CpuAnalysisModel<CpuCapture>("All Frames").apply {
        addTabModel(Tab(capture, { true }, Type.FRAMES))
        addTabModel(Tab(capture, { it.timelineEvent.isJank }, Type.JANKS))
      }
    }
    else -> null
  }

  class Tab (capture: CpuCapture, keep: (Row) -> Boolean, type: Type): CpuAnalysisTabModel<CpuCapture>(type) {
    val table by lazy {
      dataSeries.asSequence()
        .flatMap {
          val data = it.systemTraceData!!
          val lifeCycleDurationIndex = lifeCycleDurationIndex(data.getAndroidFrameLayers())
          data.getAndroidFrameTimelineEvents().asSequence().map{ it.toRow(lifeCycleDurationIndex) }
        }
        .filter(keep)
        .toMutableList()
        .asTableModel(Column::getValue, Column::type, Column::title)
    }
    init { dataSeries.add(capture) }
  }

  class Row(val timelineEvent: AndroidFrameTimelineEvent, val appUs: Long, val gpuUs: Long, val compUs: Long)

  enum class Column(val title: String, val type: Class<*>, val getValue: (Row) -> Comparable<*>) {
    FRAME_NUMBER("Frame #", Long::class.java, { it.timelineEvent.surfaceFrameToken }),
    TOTAL_TIME("Frame Duration", Long::class.java, { it.timelineEvent.actualDurationUs }),
    APP_TIME("Application", Long::class.java, Row::appUs),
    GPU_TIME("GPU", Long::class.java, Row::gpuUs),
    COMPOSITION_TIME("Composition", Long::class.java, Row::compUs)
  }

  enum class Phase { App, Gpu, Composition }

  private fun AndroidFrameTimelineEvent.toRow(lifeCycleUs: (Long, Phase) -> Long) =
    Row(this,
        lifeCycleUs(surfaceFrameToken, Phase.App),
        lifeCycleUs(surfaceFrameToken, Phase.Gpu),
        lifeCycleUs(surfaceFrameToken, Phase.Composition))

  private fun lifeCycleDurationIndex(layers: List<TraceProcessor.AndroidFrameEventsResult.Layer>): (Long, Phase) -> Long {
    fun cache(cache: MutableMap<Long, Long>, events: List<TraceProcessor.AndroidFrameEventsResult.FrameEvent>) =
      events.forEach {
        cache[it.frameNumber.toLong()] = TimeUnit.NANOSECONDS.toMicros(it.durationNanoseconds)
      }
    val appCache = mutableMapOf<Long, Long>()
    val gpuCache = mutableMapOf<Long, Long>()
    val compCache = mutableMapOf<Long, Long>()
    layers.forEach { layer ->
      layer.phaseList.forEach { phase -> when (phase.phaseName) {
        "App" -> cache(appCache, phase.frameEventList)
        "GPU" -> cache(gpuCache, phase.frameEventList)
        "Composition" -> cache(compCache, phase.frameEventList)
      }}
    }
    return { frameNumber, phase -> (when (phase) {
      Phase.App -> appCache
      Phase.Gpu -> gpuCache
      Phase.Composition -> compCache
    })[frameNumber] ?: INVALID_DURATION}
  }

  const val INVALID_DURATION = Long.MAX_VALUE
}