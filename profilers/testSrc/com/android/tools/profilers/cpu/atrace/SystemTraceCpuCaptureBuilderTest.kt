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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.systemtrace.CpuCoreModel
import com.android.tools.profilers.systemtrace.ProcessModel
import com.android.tools.profilers.systemtrace.SchedulingEventModel
import com.android.tools.profilers.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.systemtrace.ThreadModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemTraceCpuCaptureBuilderTest {

  @Test
  fun `buildCpuStateData - thread states`() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf())),
        mapOf()))

    val cpuCores = listOf(
      CpuCoreModel(0, listOf(
        SchedulingEventModel(ThreadState.RUNNING, 0L, 40L, 40L, 40L, 1, 1, 0),
        SchedulingEventModel(ThreadState.RUNNING, 60L, 90L, 30L, 30L, 1, 1, 0),
        SchedulingEventModel(ThreadState.RUNNING, 120L, 180L, 60L, 60L, 1, 1, 0))),
      CpuCoreModel(1, listOf())
    )

    val model = TestModel(processes, cpuCores)

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1)

    assertThat(capture.cpuCount).isEqualTo(2)
    // We map the values to some string representation so we can compare the content easily, because SeriesData<*> does
    // not have a proper equals override.
    assertThat(capture.getCpuThreadSliceInfoStates(0).map { "${it.x}-${it.value.processId}-${it.value.durationUs}" })
      .containsExactly("0-1-40", "40-0-0", "60-1-30", "90-0-0", "120-1-60", "200-0-0").inOrder()
    assertThat(capture.getCpuThreadSliceInfoStates(1).map { "${it.x}-${it.value.processId}-${it.value.durationUs}" })
      .containsExactly("200-0-0").inOrder()
  }

  @Test
  fun `buildCpuStateData - utilization`() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf())),
        mapOf()))

    val cpuCores = listOf(
      CpuCoreModel(0, listOf(
        SchedulingEventModel(ThreadState.RUNNING, 5000L, 10000L, 5000L, 5000L, 1, 1, 0),
        SchedulingEventModel(ThreadState.RUNNING, 25000L, 70000L, 45000L, 45000L, 1, 1, 0))),
      CpuCoreModel(1, listOf())
    )

    val model = TestModel(processes, cpuCores)

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1)

    assertThat(capture.cpuCount).isEqualTo(2)
    // We map the values to some string representation so we can compare the content easily, because SeriesData<*> does
    // not have a proper equals override.
    assertThat(capture.cpuUtilizationSeries.map { "${it.x}-${it.value}" })
      .containsExactly("0-30", "50000-20").inOrder() // First bucket = 30% utilization, Second bucket = 20% utilization.
  }

  private class TestModel(
    private val processes: Map<Int, ProcessModel>, private val cpuCores: List<CpuCoreModel>) : SystemTraceModelAdapter {

    override fun getCaptureStartTimestampUs() = 0L
    override fun getCaptureEndTimestampUs() = 200L
    override fun getProcessById(id: Int) = processes[id]
    override fun getProcesses(): List<ProcessModel> = processes.values.sortedBy { it.id }
    override fun getCpuCores(): List<CpuCoreModel> = cpuCores
    override fun getSystemTraceTechnology() = Cpu.CpuTraceType.UNSPECIFIED_TYPE
    override fun isCapturePossibleCorrupted() = false
  }
}