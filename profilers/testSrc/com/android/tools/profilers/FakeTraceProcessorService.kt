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
package com.android.tools.profilers

import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import com.android.tools.profilers.systemtrace.CpuCoreModel
import com.android.tools.profilers.systemtrace.ProcessModel
import com.android.tools.profilers.systemtrace.SystemTraceModelAdapter
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonQueryStats
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

class FakeTraceProcessorService: TraceProcessorService {

  companion object {
    private val validTraces = setOf(
      CpuProfilerTestUtils.getTraceFile("perfetto.trace"),
      CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace")
    )

    // If we are using FakeTraceProcessorService, then we pre-load both model maps so we don't slowdown tests.
    private val modelMapCache: Map<String, Map<Int, SystemTraceModelAdapter>> by lazy {
      mapOf(
        "perfetto.trace" to loadModelMapFor(CpuProfilerTestUtils.getTraceFile("perfetto.trace")),
        "perfetto_cpu_usage.trace" to loadModelMapFor(CpuProfilerTestUtils.getTraceFile("perfetto_cpu_usage.trace"))
      )
    }

    // For each known trace we store a map for each possible process id to the generated model.
    // This is to account for that during test we can not reparse the trace, so we need to have all possibilities ready.
    private fun loadModelMapFor(traceFile: File): Map<Int, SystemTraceModelAdapter> {
      val serializedModelMap = CpuProfilerTestUtils.getTraceFile("${traceFile.name}_tpd_model")

      val ois = ObjectInputStream(FileInputStream(serializedModelMap))
      @Suppress("UNCHECKED_CAST")
      val modelMap = ois.readObject() as Map<Int, SystemTraceModelAdapter>
      ois.close()

      return modelMap
    }
  }

  private val loadedTraces = mutableMapOf<Long, File>()

  override fun loadTrace(traceId: Long, traceFile: File, tracker: FeatureTracker): Boolean {
    if (validTraces.contains(traceFile)) {
      loadedTraces[traceId] = traceFile
      return true
    } else {
      loadedTraces.remove(traceId)
      return false
    }
  }
  override fun getProcessMetadata(traceId: Long, tracker: FeatureTracker): List<ProcessModel> {
    if (loadedTraces.containsKey(traceId)) {
      return loadProcessModelListFor(loadedTraces[traceId]!!)
    } else {
      return emptyList()
    }
  }

  private fun loadProcessModelListFor(traceFile: File): List<ProcessModel> {
    val serializedProcessModelList = CpuProfilerTestUtils.getTraceFile("${traceFile.name}_process_list")

    val ois = ObjectInputStream(FileInputStream(serializedProcessModelList))
    @Suppress("UNCHECKED_CAST")
    val processList = ois.readObject() as List<ProcessModel>
    ois.close()

    return processList
  }

  override fun loadCpuData(traceId: Long, processIds: List<Int>, tracker: FeatureTracker): SystemTraceModelAdapter {
    if (loadedTraces.containsKey(traceId)) {
      val cacheKey = loadedTraces[traceId]!!.name
      val model: Map<Int, SystemTraceModelAdapter> = modelMapCache[cacheKey] ?: error("$cacheKey should be present in the modelMapCache")
      // The pid of the main process is always the first one in the list.
      val pid = processIds[0]
      return model[pid] ?: error("$pid process should be present in model")
    } else {
      return EmptyModelAdapter()
    }
  }

  override fun loadMemoryData(traceId: Long,
                              abi: String,
                              symbolizer: NativeFrameSymbolizer,
                              memorySet: NativeMemoryHeapSet,
                              tracker: FeatureTracker) {
    // Will populate as needed. Currently no test rely on this.
  }

  private inner class EmptyModelAdapter: SystemTraceModelAdapter {
    override fun getCaptureStartTimestampUs() = 0L
    override fun getCaptureEndTimestampUs() = 0L
    override fun getProcesses(): List<ProcessModel> = emptyList()
    override fun getProcessById(id: Int) = getProcesses().find { it.id == id }
    override fun getCpuCores(): List<CpuCoreModel> = emptyList()
    override fun getSystemTraceTechnology() = Cpu.CpuTraceType.PERFETTO
    override fun isCapturePossibleCorrupted() = false
  }
}