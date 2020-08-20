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
package com.android.tools.profilers.perfetto.traceprocessor

import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import java.io.File

/**
 * This service manages the lifetime and connections to a TraceProcessorDaemon (TPD),
 * which is used to parse and analyse Perfetto traces.
 *
 * You need to call {@link loadTrace(Long, File)} first, before calling any method that query data from TPD.
 */
interface TraceProcessorService {

  /**
   * Load a Perfetto Trace from {@code traceFile} and assign it the {@code traceId} id internally for future queries.
   *
   * @returns true if the trace was loaded successfully.
   */
  fun loadTrace(traceId: Long, traceFile: File, tracker: FeatureTracker): Boolean

  /**
   * Query the Perfetto trace processor processes and threads information available in a trace.
   */
  fun getProcessMetadata(traceId: Long, tracker: FeatureTracker): List<ProcessModel>

  /**
   * Query the Perfetto trace processor for cpu data regarding a set of processes.
   * For example, a main process plus surfaceflinger one.
   */
  fun loadCpuData(traceId: Long, processIds: List<Int>, tracker: FeatureTracker): SystemTraceModelAdapter

  /**
   * Query the Perfetto trace processor for Heapprofd data and populate the profiler {@link NativeMemoryHeapSet} object with the results.
   */
  fun loadMemoryData(traceId: Long, abi: String, symbolizer: NativeFrameSymbolizer, memorySet: NativeMemoryHeapSet, tracker: FeatureTracker)
}