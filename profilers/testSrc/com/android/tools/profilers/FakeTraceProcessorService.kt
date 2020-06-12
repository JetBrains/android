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
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import com.android.tools.profilers.systemtrace.CpuCoreModel
import com.android.tools.profilers.systemtrace.ProcessModel
import com.android.tools.profilers.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.systemtrace.ThreadModel
import java.io.File
import java.lang.UnsupportedOperationException

class FakeTraceProcessorService: TraceProcessorService {
  val PROCESSES = mapOf(
    1 to ProcessModel(1, "process_p1",
                            mapOf(
                              1 to ThreadModel(1, 1, "p1_fake_thread_1", listOf(), listOf()),
                              2 to ThreadModel(2, 1, "p1_fake_thread_2", listOf(), listOf())
                            ),
                            mapOf()),
    2 to ProcessModel(2, "process_p2",
                      mapOf(
                        6 to ThreadModel(6, 2, "p2_fake_thread_1", listOf(), listOf())
                      ),
                      mapOf())).toSortedMap()

  val CORES = listOf(
    CpuCoreModel(0, listOf()), CpuCoreModel(1, listOf()), CpuCoreModel(2, listOf()), CpuCoreModel(3, listOf()))

  override fun loadTrace(traceId: Long, traceFile: File) { }
  override fun getProcessMetadata(traceId: Long) = PROCESSES.values.toList()

  override fun loadCpuData(traceId: Long, processIds: List<Int>): SystemTraceModelAdapter {
    // Will populate as needed. Currently no test rely on this.
    return FakeModelAdapter()
  }

  override fun loadMemoryData(traceId: Long, abi: String, symbolizer: NativeFrameSymbolizer, memorySet: NativeMemoryHeapSet) {
    // Will populate as needed. Currently no test rely on this.
  }

  private inner class FakeModelAdapter: SystemTraceModelAdapter {
    override fun getCaptureStartTimestampUs() = 0L
    override fun getCaptureEndTimestampUs() = 10000L
    override fun getProcesses(): List<ProcessModel> = PROCESSES.values.toList()
    override fun getProcessById(id: Int) = getProcesses().find { it.id == id }
    override fun getCpuCores(): List<CpuCoreModel> = CORES
    override fun getSystemTraceTechnology() = Cpu.CpuTraceType.PERFETTO
    override fun isCapturePossibleCorrupted() = false
  }
}