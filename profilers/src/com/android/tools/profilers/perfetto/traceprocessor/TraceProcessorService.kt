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

import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
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
  fun loadTrace(traceId: Long, traceFile: File, ideProfilerServices: IdeProfilerServices): Boolean

  /**
   * Query the Perfetto trace processor processes and threads information available in a trace.
   */
  fun getProcessMetadata(traceId: Long, ideProfilerServices: IdeProfilerServices): List<ProcessModel>

  /**
   * Query the Perfetto trace processor for cpu data regarding a set of processes.
   *
   * @param processIds set of processes to get CPU data for, e.g. a main process plus surfaceflinger one.
   * @param selectedProcessName name of the selected application process, useful for retrieving Android frame events.
   */
  fun loadCpuData(traceId: Long,
                  processIds: List<Int>,
                  selectedProcessName: String,
                  ideProfilerServices: IdeProfilerServices): SystemTraceModelAdapter

  /**
   * Query the Perfetto trace processor for Heapprofd data and populate the profiler {@link NativeMemoryHeapSet} object with the results.
   */
  fun loadMemoryData(traceId: Long, abi: String, memorySet: NativeMemoryHeapSet, ideProfilerServices: IdeProfilerServices)

  /**
   * Query the trace metadata from the metadata table. https://perfetto.dev/docs/analysis/sql-tables#metadata
   * If the metadataName is blank or has multiple rows with the same name, the value of each row is returned.
   * If an error occurs, or the metadata is not found an empty list is returned.
   */
  fun getTraceMetadata(traceId: Long, metadataName: String, ideProfilerServices: IdeProfilerServices) : List<String>
}