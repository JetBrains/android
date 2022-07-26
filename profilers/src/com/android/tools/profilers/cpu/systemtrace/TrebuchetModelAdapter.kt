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

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.cpu.ThreadState
import trebuchet.model.CpuProcessSlice
import trebuchet.model.Model
import trebuchet.model.SchedSlice
import trebuchet.model.SchedulingState
import trebuchet.model.base.SliceGroup
import java.util.concurrent.TimeUnit

class TrebuchetModelAdapter(trebuchetModel: Model, private val technology: Cpu.CpuTraceType) : SystemTraceModelAdapter {

  companion object {
    private val SECONDS_TO_US = TimeUnit.SECONDS.toMicros(1)

    private fun convertSecondsToUs(seconds: Double): Long = (SECONDS_TO_US * seconds).toLong()
  }

  private val beginTimestampSeconds: Double = trebuchetModel.beginTimestamp
  private val endTimestampSeconds: Double = trebuchetModel.endTimestamp
  private val timeShiftFromBeginningSeconds: Double

  // The realtime timestamp is written out as soon as we start
  // an atrace capture. If this timestamp does not exist this value
  // will be 0. The value wont exist if the user attempted to capture
  // more data than the size of the existing buffer.
  private val possibleCorruption = trebuchetModel.realtimeTimestamp == 0L

  private val processById = sortedMapOf<Int, ProcessModel>()
  private val cores: List<CpuCoreModel>

  override fun getCaptureStartTimestampUs() = convertToUserTimeUs(beginTimestampSeconds)
  override fun getCaptureEndTimestampUs() = convertToUserTimeUs(endTimestampSeconds)

  override fun getProcessById(id: Int): ProcessModel? = processById[id]
  override fun getProcesses(): List<ProcessModel> = processById.values.toList()
  override fun getDanglingThread(tid: Int): ThreadModel? = null

  override fun getCpuCores(): List<CpuCoreModel> = cores

  override fun getSystemTraceTechnology() = technology
  override fun isCapturePossibleCorrupted() = possibleCorruption

  /**
   * Android frame events are not supported in Trebuchet.
   */
  override fun getAndroidFrameLayers() = emptyList<TraceProcessor.AndroidFrameEventsResult.Layer>()
  override fun getAndroidFrameTimelineEvents() = emptyList<AndroidFrameTimelineEvent>()

  init {
    // We check if we have a parent timestamp. If not this could be from an imported trace.
    // In the case it is 0, we use the first timestamp of our capture as a reference point.
    if (trebuchetModel.parentTimestamp.compareTo(0.0) == 0) {
      timeShiftFromBeginningSeconds = 0.0
    }
    else {
      timeShiftFromBeginningSeconds = trebuchetModel.parentTimestamp - trebuchetModel.parentTimestampBootTime
    }

    for (process in trebuchetModel.processes.values) {
      val threadMap = mutableMapOf<Int, ThreadModel>()
      for (thread in process.threads) {
        val traceEvents = mapSlicesToTraceEvents(thread.slices)
        val schedEvents = mapSchedSliceToSchedEvent(thread.schedSlices, process.id, thread.id)
        threadMap[thread.id] = ThreadModel(thread.id, process.id, thread.name, traceEvents, schedEvents)
      }

      val counterMap = mutableMapOf<String, CounterModel>()
      for (counter in process.counters) {
        counterMap[counter.name] = CounterModel(counter.name,
                                                counter.events
                                                  .associate { convertToUserTimeUs(it.timestamp) to it.count.toDouble() }
                                                  .toSortedMap())
      }
      processById[process.id] = ProcessModel(process.id, process.name, threadMap, counterMap)
    }

    // TODO(b/162354761): implement counters for Trebuchet.
    cores = trebuchetModel.cpus
      .map { cpu -> CpuCoreModel(cpu.id, mapCpuProcessSliceToSchedEvent(cpu.slices, cpu.id), emptyMap()) }
      .sortedBy { core -> core.id }
  }

  private fun mapSlicesToTraceEvents(slices: List<SliceGroup>): List<TraceEventModel> = slices.map {
    TraceEventModel(
      it.name,
      convertToUserTimeUs(it.startTime),
      convertToUserTimeUs(it.endTime),
      convertSecondsToUs(it.cpuTime),
      mapSlicesToTraceEvents(it.children))
  }

  private fun mapSchedSliceToSchedEvent(slices: List<SchedSlice>, pid: Int, tid: Int): List<SchedulingEventModel> = slices.map {
    SchedulingEventModel(
      convertSchedulingState(it),
      convertToUserTimeUs(it.startTime),
      convertToUserTimeUs(it.endTime),
      convertSecondsToUs(it.duration),
      convertSecondsToUs(it.cpuTime),
      pid,
      tid,
      0)
  }

  private fun mapCpuProcessSliceToSchedEvent(slices: List<CpuProcessSlice>, core: Int): List<SchedulingEventModel> = slices.map {
    SchedulingEventModel(
      ThreadState.RUNNING_CAPTURED,
      convertToUserTimeUs(it.startTime),
      convertToUserTimeUs(it.endTime),
      convertSecondsToUs(it.duration),
      convertSecondsToUs(it.cpuTime),
      it.id,
      it.threadId,
      core)
  }

  private fun convertSchedulingState(slice: SchedSlice): ThreadState {
    return when (slice.state) {
      SchedulingState.RUNNING -> ThreadState.RUNNING_CAPTURED
      SchedulingState.WAKING, SchedulingState.RUNNABLE -> ThreadState.RUNNABLE_CAPTURED
      SchedulingState.EXIT_DEAD -> ThreadState.DEAD_CAPTURED
      SchedulingState.SLEEPING -> ThreadState.SLEEPING_CAPTURED
      SchedulingState.UNINTR_SLEEP -> ThreadState.WAITING_CAPTURED
      SchedulingState.UNINTR_SLEEP_IO -> ThreadState.WAITING_IO_CAPTURED
      else -> ThreadState.UNKNOWN
    }
  }

  private fun convertToUserTimeUs(timestampInSeconds: Double): Long {
    return convertSecondsToUs(timestampInSeconds + timeShiftFromBeginningSeconds)
  }
}