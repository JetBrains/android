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
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.cpu.ThreadState
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
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.getCpuCount()).isEqualTo(2)
    // We map the values to some string representation so we can compare the content easily, because SeriesData<*> does
    // not have a proper equals override.
    assertThat(systemTraceData.getCpuThreadSliceInfoStates(0).map { "${it.x}-${it.value.processId}-${it.value.durationUs}" })
      .containsExactly("0-1-40", "40-0-0", "60-1-30", "90-0-0", "120-1-60", "200-0-0").inOrder()
    assertThat(systemTraceData.getCpuThreadSliceInfoStates(1).map { "${it.x}-${it.value.processId}-${it.value.durationUs}" })
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
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.getCpuCount()).isEqualTo(2)
    // We map the values to some string representation so we can compare the content easily, because SeriesData<*> does
    // not have a proper equals override.
    assertThat(systemTraceData.getCpuUtilizationSeries().map { "${it.x}-${it.value}" })
      .containsExactly("0-30", "50000-20").inOrder() // First bucket = 30% utilization, Second bucket = 20% utilization.
  }

  @Test
  fun buildMainProcessMemoryCountersData() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf())),
        mapOf(
          // Will get these:
          "mem.rss" to CounterModel("rss", sortedMapOf(1L to 5.0, 4L to 6.0, 7L to 4.0)),
          "mem.locked" to CounterModel("rss", sortedMapOf(1L to 1.0, 4L to 2.0, 7L to 1.0)),
          // Will not get this, because they doesn't start with "mem."
          "rss" to CounterModel("rss", sortedMapOf(50L to 10.0, 100L to 90.0, 101L to 75.0)),
          "nonmem.rss" to CounterModel("nonmem.rss", sortedMapOf(50L to 10.0, 100L to 90.0, 101L to 75.0)),
          "non-memory" to CounterModel("non-memory", sortedMapOf(50L to 10.0, 100L to 90.0, 101L to 75.0)))))

    val model = TestModel(processes, listOf())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1)
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.getMemoryCounters()).hasSize(2)
    assertThat(systemTraceData.getMemoryCounters()).containsExactly(
      "mem.locked", listOf(
        SeriesData(1, 1L),
        SeriesData(4, 2L),
        SeriesData(7, 1L)),
      "mem.rss", listOf(
        SeriesData(1, 5L),
        SeriesData(4, 6L),
        SeriesData(7, 4L)))
      .inOrder()

    assertThat(systemTraceData.getMemoryCounters()).doesNotContainKey("non-memory")
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