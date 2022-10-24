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

import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame.FrameThread

/**
 * This interface contains methods to access data only available in SystemTrace captures.
 */
interface CpuSystemTraceData {

  /**
   * Returns the thread state transitions for the given thread.
   *
   * @param threadId Thread Id of thread requesting states for. If thread id is not found an empty list is returned.
   */
  fun getThreadStatesForThread(threadId: Int): List<SeriesData<ThreadState>>

  /**
   * Returns a series of [CpuThreadSliceInfo] information.
   * @param cpu The cpu index to get [CpuThreadSliceInfo] series for.
   */
  fun getCpuThreadSliceInfoStates(cpu: Int): List<SeriesData<CpuThreadSliceInfo>>

  /**
   * CPU Utilization data series, with one for each CPU core present on the traced device.
   */
  val cpuUtilizationSeries: List<SeriesData<Long>>

  /**
   * The number of CPU cores represented in this capture.
   */
  val cpuCount: Int get() = cpuUtilizationSeries.size

  /**
   * The memory counters for the process selected for this capture.
   *
   * For each memory counter (those that starts with "mem.", like "mem.locked", "mem.rss", "mem.rss.anon", "mem.rss.file",
   * "mem.rss.shmem", "mem.rss.watermark", "mem.swap" and "mem.virt") present in the main process, the retuned map will contain
   * the corresponding data series for that counter.
   */
  val memoryCounters: Map<String, List<SeriesData<Long>>>

  /**
   * The power rail counters for the process selected for this capture.
   *
   * For each power rail counter (those that starts with "power.rails.", like "power.rails.modem") present in the system.
   * The returned map will contain the corresponding data series for that counter.
   */
  val powerRailCounters: Map<String, List<SeriesData<Long>>>

  /**
   * The battery drain counters for the process selected for this capture.
   *
   * For each battery drain counter (those that starts with "batt.", like "batt.charge") present in the system.
   * The returned map will contain the corresponding data series for that counter.
   */
  val batteryDrainCounters: Map<String, List<SeriesData<Long>>>

  /**
   * The CPU counters by CPU core.
   *
   * Each element of the list is a map of counter name to counter values for one CPU core.
   * Currently supported counters are "cpufreq" and "cpuidle".
   */
  val cpuCounters: List<Map<String, List<SeriesData<Long>>>>

  /**
   * If the capture is potentially missing data. For example, on a ATrace or Perfetto capture,
   * due to the capture buffer being a ring buffer.
   */
  val isMissingData: Boolean

  // GPU/Frames data
  /**
   * @return a data series with frame performance classes sorted by frame start time.
   */
  fun getFrames(threadType: FrameThread): List<SeriesData<SystemTraceFrame>>

  /**
   * A data series with Surfaceflinger events.
   */
  val surfaceflingerEvents: List<SeriesData<SurfaceflingerEvent>>

  /**
   * A data series with VSYNC-sf counter (0 or 1).
   */
  val vsyncCounterValues: List<SeriesData<Long>>

  /**
   * A data series with BufferQueue (SurfaceView) counter (0, 1, or 2).
   */
  val bufferQueueCounterValues: List<SeriesData<Long>>

  /**
   * The thread id of thread matching name of the render thread.
   */
  val renderThreadId: Int

  /**
   * Android frame events organized in phases (e.g. Display, GPU) by Layer. Supported since Android R.
   */
  val androidFrameLayers: List<TraceProcessor.AndroidFrameEventsResult.Layer>

  /**
   * Android FrameTimeline events for jank detection. Supported since Android S.
   */
  val androidFrameTimelineEvents: List<AndroidFrameTimelineEvent>
}