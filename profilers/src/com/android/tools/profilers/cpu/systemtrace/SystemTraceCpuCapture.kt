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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.CachedFunction
import com.android.tools.profilers.CappedLRUMap
import com.android.tools.profilers.cpu.BaseCpuCapture
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame.FrameThread
import kotlin.streams.asSequence
import kotlin.streams.toList

class SystemTraceCpuCapture(traceId: Long,
                            model: SystemTraceModelAdapter,
                            captureNodes: Map<CpuThreadInfo, CaptureNode>,
                            private val threadStateDataSeries: Map<Int, List<SeriesData<ThreadState>>>,
                            private val cpuThreadSliceInfoStates: Map<Int, List<SeriesData<CpuThreadSliceInfo>>>,
                            override val cpuUtilizationSeries: List<SeriesData<Long>>,
                            override val cpuCounters: List<Map<String, List<SeriesData<Long>>>>,
                            override val memoryCounters: Map<String, List<SeriesData<Long>>>,
                            private val blastBufferQueueCounter: List<SeriesData<Long>>,
                            private val frameManager: SystemTraceFrameManager,
                            private val surfaceflingerManager: SystemTraceSurfaceflingerManager,
                            initialViewRangeUs: Range)
          // System Traces don't support dual clock.
          : BaseCpuCapture(traceId, model.getSystemTraceTechnology(), false, null,
                           Range(model.getCaptureStartTimestampUs().toDouble(), model.getCaptureEndTimestampUs().toDouble()),
                           captureNodes)
           , CpuSystemTraceData {
  override val isMissingData = model.isCapturePossibleCorrupted()
  override val androidFrameLayers = model.getAndroidFrameLayers()
  override val androidFrameTimelineEvents = model.getAndroidFrameTimelineEvents()
  override val cpuCount get() = cpuThreadSliceInfoStates.size
  override val surfaceflingerEvents get() = surfaceflingerManager.surfaceflingerEvents
  override val vsyncCounterValues get() = surfaceflingerManager.vsyncCounterValues
  override val renderThreadId get() = frameManager.renderThreadId
  private val gpuThreadId by lazy { threads.first { it.isGpuThread }.id }

  // Each search is a full scan over the thread's nodes, and GPU's result is dependent on Render's,
  // and this is potentially called many times during rendering, so we cache the few most recent ones.
  // The cache isn't weak, but it's small and it dies with this capture, so there shouldn't be any serious leak.
  val frameRenderSequence: (AndroidFrameTimelineEvent) -> RenderSequence = CachedFunction(CappedLRUMap(10)) { event ->
    fun eventByName(threadId: Int, name: String) =
      getCaptureNode(threadId)?.descendantsStream?.asSequence()?.find { it.data.nameWithSuffix == name }
    val mainThreadEvent = eventByName(mainThreadId, "$MAIN_THREAD_EVENT_PREFIX ${event.surfaceFrameToken}")
    val renderThreadEvent = eventByName(renderThreadId, "$RENDER_THREAD_EVENT_PREFIX ${event.surfaceFrameToken}")
    val gpuThreadEvent = renderThreadEvent?.let {
      fun eventsFromEndToHere(threadId: Int, prefix: String) = getCaptureNode(threadId)!!.descendantsStream
        .filter { it.data.name.startsWith(prefix) }
        .sorted(compareByDescending(CaptureNode::startGlobal))
        .takeWhile { renderThreadEvent.startGlobal <= it.startGlobal }
      var remainingRenderEvents = eventsFromEndToHere(renderThreadId, RENDER_THREAD_EVENT_PREFIX).toList()
      eventsFromEndToHere(gpuThreadId, GPU_THREAD_EVENT_PREFIX).asSequence()
        .find { gpu ->
          val i = remainingRenderEvents.indexOfFirst { it.startGlobal < gpu.startGlobal }
          when {
            i < 0 -> false
            remainingRenderEvents[i] === renderThreadEvent -> true
            else -> false.also {
              remainingRenderEvents = remainingRenderEvents.subList(i + 1, remainingRenderEvents.size)
            }
          }
        }
    }
    RenderSequence(mainThreadEvent, renderThreadEvent, gpuThreadEvent)
  }

  init {
    // Set the view range of the capture timeline to our initial view range, this is used later by the UI to set the initial view.
    timeline.viewRange[initialViewRangeUs.min] = initialViewRangeUs.max
  }

  // SurfaceFlinger buffer queue counter on pre-S and BLAST buffer queue counter on S+.
  override val bufferQueueCounterValues get() = when {
    surfaceflingerManager.bufferQueueValues.isEmpty() -> blastBufferQueueCounter
    else -> surfaceflingerManager.bufferQueueValues
  }

  /**
   * The thread states are computed from the sched_switch trace line reported by an atrace capture.
   * Atrace reports a sched_switch event each time the thread state changes, because of this the thread states
   * reported here are more accurate than the ones sampled via perfd.
   */
  override fun getThreadStatesForThread(threadId: Int) = threadStateDataSeries[threadId] ?: listOf()

  /**
   * The information is computed from the sched_switch trace line reported by atrace.
   */
  override fun getCpuThreadSliceInfoStates(cpu: Int) = cpuThreadSliceInfoStates[cpu] ?: listOf()
  override fun getFrames(threadType: FrameThread) = frameManager.getFrames(threadType)
  override fun getSystemTraceData() = this

  companion object {
    const val MAIN_THREAD_EVENT_PREFIX = "Choreographer#doFrame"
    const val RENDER_THREAD_EVENT_PREFIX = "DrawFrames"
    const val GPU_THREAD_EVENT_PREFIX = "waiting for GPU completion"
  }
}

data class RenderSequence(val mainEvent: CaptureNode?, val renderEvent: CaptureNode?, val gpuEvent: CaptureNode?)