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
package com.android.tools.profilers.systemtrace

import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.cpu.ThreadState
import java.util.SortedMap

/**
 * SystemTraceModelAdapter exposes a common API for accessing the raw model data from system trace
 * captures.
 *
 * This should be used in order to compute data series and nodes that we will display in the UI.
 */
interface SystemTraceModelAdapter {

  fun getCaptureStartTimestampUs(): Long
  fun getCaptureEndTimestampUs(): Long

  fun getProcessById(id: Int): ProcessModel?
  fun getProcesses(): List<ProcessModel>

  fun getCpuCores(): List<CpuCoreModel>

  fun getSystemTraceTechnology(): Cpu.CpuTraceType

  /**
   * Returns true if there is potentially missing data from the capture.
   * It's hard to guarantee if data is missing or not, so this is a best guess.
   */
  fun isCapturePossibleCorrupted(): Boolean
}

data class ProcessModel(
  val id: Int,
  val name: String,
  val threadById: Map<Int, ThreadModel>,
  val counterByName: Map<String, CounterModel>) {

  fun getMainThread(): ThreadModel? = threadById[id]
  fun getThreads() = threadById.values

  /**
   * Returns the best assumed name for a process.
   * If the process does not have a name it looks at the name of the main thread, but if we also
   * have no information on the main thread it returns "<PID>" instead.
   */
  fun getSafeProcessName(): String {
    if (name.isNotBlank() && !name.startsWith("<")) {
      return name
    }

    // Fallback to the main thread name
    val mainThreadName = getMainThread()?.name ?: ""
    return if (mainThreadName.isNotBlank()) {
      mainThreadName
    } else {
      "<$id>"
    }
  }
}

data class ThreadModel(
  val id: Int,
  val tgid: Int,
  val name: String,
  val traceEvents: List<TraceEventModel>,
  val schedulingEvents: List<SchedulingEventModel>)

data class TraceEventModel (
  val name: String,
  val startTimestampUs: Long,
  val endTimestampUs: Long,
  val cpuTimeUs: Long,
  val childrenEvents: List<TraceEventModel>)

data class SchedulingEventModel(
  val state: ThreadState,
  val startTimestampUs: Long,
  val endTimestampUs: Long,
  val durationUs: Long,
  val cpuTimeUs: Long,
  val processId: Int,
  val threadId: Int,
  val core: Int)

data class CounterModel(
  val name: String,
  val valuesByTimestampUs: SortedMap<Long, Double>)

data class CpuCoreModel(
  val id: Int,
  val schedulingEvents: List<SchedulingEventModel>)