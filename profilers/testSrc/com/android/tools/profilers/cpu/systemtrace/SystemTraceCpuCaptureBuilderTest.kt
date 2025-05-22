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
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.ThreadState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import perfetto.protos.PerfettoTrace
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType

class SystemTraceCpuCaptureBuilderTest {

  @Test
  fun `buildThreadStateData - termination state present`() {
    val sleepingThread = ThreadModel(1, 1, "ENDS_WITH_SLEEPING_STATE_THREAD",
                                     listOf(),
                                     listOf(SchedulingEventModel(ThreadState.SLEEPING_CAPTURED, 0L, 5L, 5L, 5L, 1, 1, 1)),
                                     listOf(ThreadStateModel(ThreadState.SLEEPING_CAPTURED, 0L, 5L)))
    val runningThread = ThreadModel(2, 2, "ENDS_WITH_RUNNING_STATE_THREAD",
                                    listOf(),
                                    listOf(SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 0L, 5L, 5L, 5L, 1, 1, 1)),
                                    listOf(ThreadStateModel(ThreadState.RUNNING_CAPTURED, 0L, 5L)))
    val waitingThread = ThreadModel(3, 3, "ENDS_WITH_WAITING_STATE_THREAD",
                                    listOf(),
                                    listOf(SchedulingEventModel(ThreadState.WAITING_CAPTURED, 0L, 5L, 5L, 5L, 1, 1, 1)),
                                    listOf(ThreadStateModel(ThreadState.WAITING_CAPTURED, 0L, 5L)))
    val deadThread = ThreadModel(4, 4, "ENDS_WITH_DEAD_STATE_THREAD",
                                    listOf(),
                                    listOf(SchedulingEventModel(ThreadState.DEAD_CAPTURED, 0L, 5L, 5L, 5L, 1, 1, 1)),
                                    listOf(ThreadStateModel(ThreadState.DEAD_CAPTURED, 0L, 5L)))
    val unknownThread = ThreadModel(5, 5, "ENDS_WITH_UNKNOWN_STATE_THREAD",
                                    listOf(),
                                    listOf(SchedulingEventModel(ThreadState.UNKNOWN, 0L, 5L, 5L, 5L, 1, 1, 1)),
                                    listOf(ThreadStateModel(ThreadState.UNKNOWN, 0L, 5L)))

    val processes = mapOf(1 to ProcessModel(
      1, "Process",
      mapOf(1 to sleepingThread, 2 to runningThread, 3 to waitingThread, 4 to deadThread, 5 to unknownThread),
      mapOf()
    ))

    val model = TestModel(processes, mapOf(), listOf(), listOf(), listOf())
    val capture = SystemTraceCpuCaptureBuilder(model).build(0L, 1, Range(0.0, 5.0))
    val systemTraceData = capture.systemTraceData

    // Check if the fake/termination NO_ACTIVITY thread status is added successfully
    // and that it uses the original last thread status' end timestamp as its start.
    assertThat(systemTraceData.getThreadStatesForThread(1).size).isEqualTo(2)
    assertThat(systemTraceData.getThreadStatesForThread(1).last().value).isEqualTo(ThreadState.NO_ACTIVITY)
    assertThat(systemTraceData.getThreadStatesForThread(1).last().x).isEqualTo(5)

    assertThat(systemTraceData.getThreadStatesForThread(2).size).isEqualTo(2)
    assertThat(systemTraceData.getThreadStatesForThread(2).last().value).isEqualTo(ThreadState.NO_ACTIVITY)
    assertThat(systemTraceData.getThreadStatesForThread(2).last().x).isEqualTo(5)

    assertThat(systemTraceData.getThreadStatesForThread(3).size).isEqualTo(2)
    assertThat(systemTraceData.getThreadStatesForThread(3).last().value).isEqualTo(ThreadState.NO_ACTIVITY)
    assertThat(systemTraceData.getThreadStatesForThread(3).last().x).isEqualTo(5)

    assertThat(systemTraceData.getThreadStatesForThread(4).size).isEqualTo(2)
    assertThat(systemTraceData.getThreadStatesForThread(4).last().value).isEqualTo(ThreadState.NO_ACTIVITY)
    assertThat(systemTraceData.getThreadStatesForThread(4).last().x).isEqualTo(5)

    assertThat(systemTraceData.getThreadStatesForThread(5).size).isEqualTo(2)
    assertThat(systemTraceData.getThreadStatesForThread(5).last().value).isEqualTo(ThreadState.NO_ACTIVITY)
    assertThat(systemTraceData.getThreadStatesForThread(5).last().x).isEqualTo(5)
  }

  @Test
  fun `buildThreadStateData - termination state not present`() {
    val noActivityThread = ThreadModel(1, 1, "ENDS_WITH_NO_ACTIVITY_STATE_THREAD",
                                       listOf(),
                                       listOf(
                                         SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 0L, 5L, 5L, 5L, 1, 1, 1),
                                         SchedulingEventModel(ThreadState.NO_ACTIVITY, 0L, 5L, 5L, 5L, 1, 1, 1),
                                       ),
                                       listOf(
                                         ThreadStateModel(ThreadState.RUNNING_CAPTURED, 0L, 5L),
                                         ThreadStateModel(ThreadState.NO_ACTIVITY, 5L, 10L)))
    val emptyThread = ThreadModel(2, 2, "NO_STATE_THREAD",
                                  listOf(), listOf(), listOf())


    val processes = mapOf(1 to ProcessModel(
      1, "Process",
      mapOf(1 to noActivityThread, 2 to emptyThread),
      mapOf()
    ))

    val model = TestModel(processes, mapOf(), listOf(), listOf(), listOf())
    val capture = SystemTraceCpuCaptureBuilder(model).build(0L, 1, Range(0.0, 5.0))
    val systemTraceData = capture.systemTraceData

    // Check if the fake/termination NO_ACTIVITY thread state is not added
    // when actual last state was NO_ACTIVITY.
    assertThat(systemTraceData.getThreadStatesForThread(1).size).isEqualTo(2)
    assertThat(systemTraceData.getThreadStatesForThread(1).last().value).isEqualTo(ThreadState.NO_ACTIVITY)

    // Make sure fake/termination NO_ACTIVITY thread state is not added
    // when there is no states in the thread to begin with.
    assertThat(systemTraceData.getThreadStatesForThread(2).size).isEqualTo(0)
  }

  @Test
  fun `buildThreadStateData - main thread name not present`() {
    val mainThread = ThreadModel(1, 1, "",
                                 listOf(),
                                 listOf(
                                   SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 0L, 5L, 5L, 5L, 1, 1, 1),
                                   SchedulingEventModel(ThreadState.NO_ACTIVITY, 0L, 5L, 5L, 5L, 1, 1, 1),
                                 ),
                                 listOf())
    val nonMainThread = ThreadModel(2, 2, "NON_MAIN_THREAD", listOf(), listOf(), listOf())

    val processes = mapOf(1 to ProcessModel(
      1, "Main Process",
      mapOf(1 to mainThread, 2 to nonMainThread),
      mapOf(),
    ))

    val model = TestModel(processes, mapOf(), listOf(), listOf(), listOf())
    val capture = SystemTraceCpuCaptureBuilder(model).build(1L, 1, Range(0.0, 5.0))
    val systemTraceData = capture.systemTraceData

    // Because the main thread name was not present, the main thread assumes the name of the process it belongs to.
    assertThat(systemTraceData.threads.find { it.isMainThread }?.name).isEqualTo("Main Process")
  }

  @Test
  fun `buildCpuStateData - thread states`() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    val danglingThreads = mapOf(33 to ThreadModel(33, 0, "DanglingThread", listOf(), listOf(), listOf()))

    val cpuCores = listOf(
      CpuCoreModel(
        0,
        listOf(
          SchedulingEventModel(ThreadState.RUNNING, 0L, 40L, 40L, 40L, 1, 1, 0),
          SchedulingEventModel(ThreadState.RUNNING, 45L, 55L, 10L, 10L, 0, 33, 0),
          SchedulingEventModel(ThreadState.RUNNING, 60L, 90L, 30L, 30L, 1, 1, 0),
          SchedulingEventModel(ThreadState.RUNNING, 120L, 180L, 60L, 60L, 1, 1, 0)),
        mapOf()
      ),
      CpuCoreModel(1, listOf(), mapOf())
    )

    val model = TestModel(processes, danglingThreads, cpuCores, emptyList(), emptyList())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range())
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.cpuCount).isEqualTo(2)
    // We map the values to some string representation so we can compare the content easily, because SeriesData<*> does
    // not have a proper equals override.
    assertThat(systemTraceData.getCpuThreadSliceInfoStates(0).map { "${it.x}-${it.value.processId}-${it.value.durationUs}" })
      .containsExactly("0-1-40", "40-0-0", "45-0-10", "55-0-0", "60-1-30", "90-0-0", "120-1-60", "200-0-0").inOrder()
    assertThat(systemTraceData.getCpuThreadSliceInfoStates(0).filter { it.value.name == "DanglingThread" }).hasSize(1)
    assertThat(systemTraceData.getCpuThreadSliceInfoStates(1).map { "${it.x}-${it.value.processId}-${it.value.durationUs}" })
      .containsExactly("200-0-0").inOrder()
  }

  @Test
  fun `buildCpuStateData - utilization`() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    val cpuCores = listOf(
      CpuCoreModel(
        0,
        listOf(
          SchedulingEventModel(ThreadState.RUNNING, 5000L, 10000L, 5000L, 5000L, 1, 1, 0),
          SchedulingEventModel(ThreadState.RUNNING, 25000L, 70000L, 45000L, 45000L, 1, 1, 0)),
        mapOf()
      ),
      CpuCoreModel(1, listOf(), mapOf())
    )

    val model = TestModel(processes, emptyMap(), cpuCores, emptyList(), emptyList())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range())
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.cpuCount).isEqualTo(2)
    // We map the values to some string representation so we can compare the content easily, because SeriesData<*> does
    // not have a proper equals override.
    assertThat(systemTraceData.cpuUtilizationSeries.map { "${it.x}-${it.value}" })
      .containsExactly("0-30", "50000-20").inOrder() // First bucket = 30% utilization, Second bucket = 20% utilization.
  }

  @Test
  fun buildCpuCounters() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    val cpuCores = listOf(
      CpuCoreModel(
        0,
        listOf(),
        mapOf(
          "cpufreq" to CounterModel("cpufreq", sortedMapOf(1L to 0.0, 2L to 1000.0)),
          "cpuidle" to CounterModel("cpuidel", sortedMapOf(2L to 0.0, 3L to 4294967295.0))
        )
      ),
      CpuCoreModel(
        1,
        listOf(),
        mapOf(
          "cpufreq" to CounterModel("cpufreq", sortedMapOf(10L to 2000.0)),
          "cpuidle" to CounterModel("cpuidle", sortedMapOf(20L to 0.0))
        ))
    )

    val model = TestModel(processes, emptyMap(), cpuCores, emptyList(), emptyList())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range())
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.cpuCount).isEqualTo(2)
    assertThat(systemTraceData.cpuCounters[0]).containsExactly(
      "cpufreq", listOf(SeriesData(1, 0L), SeriesData(2, 1000L)),
      "cpuidle", listOf(SeriesData(2, 0L), SeriesData(3, 4294967295L)))
    assertThat(systemTraceData.cpuCounters[1]).containsExactly(
      "cpufreq", listOf(SeriesData(10, 2000L)),
      "cpuidle", listOf(SeriesData(20, 0L)))
  }

  @Test
  fun buildMainProcessMemoryCountersData() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf(
          // Will get these:
          "mem.rss" to CounterModel("rss", sortedMapOf(1L to 5.0, 4L to 6.0, 7L to 4.0)),
          "mem.locked" to CounterModel("rss", sortedMapOf(1L to 1.0, 4L to 2.0, 7L to 1.0)),
          // Will not get this, because they doesn't start with "mem."
          "rss" to CounterModel("rss", sortedMapOf(50L to 10.0, 100L to 90.0, 101L to 75.0)),
          "nonmem.rss" to CounterModel("nonmem.rss", sortedMapOf(50L to 10.0, 100L to 90.0, 101L to 75.0)),
          "non-memory" to CounterModel("non-memory", sortedMapOf(50L to 10.0, 100L to 90.0, 101L to 75.0)))))

    val model = TestModel(processes, emptyMap(), listOf(), listOf(), listOf())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range())
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.memoryCounters).hasSize(2)
    assertThat(systemTraceData.memoryCounters).containsExactly(
      "mem.locked", listOf(
      SeriesData(1, 1L),
      SeriesData(4, 2L),
      SeriesData(7, 1L)),
      "mem.rss", listOf(
      SeriesData(1, 5L),
      SeriesData(4, 6L),
      SeriesData(7, 4L)))
      .inOrder()

    assertThat(systemTraceData.memoryCounters).doesNotContainKey("non-memory")
  }

  @Test
  fun buildPowerCountersDataCumulativeView() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    // The rails 'power.rails.ddr.a' & 'power.rails.ddr.c' are used as they are known to be grouped under "Memory".
    // The 'power.rails.cpu.big' is in a group "CPU Big' by itself.
    val powerRails = listOf(
      CounterModel("power.rails.ddr.a", sortedMapOf(1L to 100.0, 2L to 200.0)),
      CounterModel("power.rails.ddr.c", sortedMapOf(3L to 300.0, 4L to 400.0)),
      CounterModel("power.rails.cpu.big", sortedMapOf(5L to 500.0, 6L to 600.0)))

    val batteryDrain = listOf(
      CounterModel("foo", sortedMapOf(1L to 100.0, 2L to 200.0)),
      CounterModel("bar", sortedMapOf(3L to 300.0, 4L to 400.0)))

    val model = TestModel(processes, emptyMap(), emptyList(), powerRails, batteryDrain, emptyList())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range())
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.powerRailCounters).hasSize(2)
    assertThat(systemTraceData.batteryDrainCounters).hasSize(2)

    assertThat(systemTraceData.powerRailCounters["Memory"]!!.cumulativeData).containsExactly(
      SeriesData(3, 500L),
      SeriesData(4, 600L))
      .inOrder()

    assertThat(systemTraceData.powerRailCounters["CPU Big"]!!.cumulativeData).containsExactly(
      SeriesData(5, 500L),
      SeriesData(6, 600L))
      .inOrder()

    assertThat(systemTraceData.batteryDrainCounters).containsExactly(
      "bar", listOf(
      SeriesData(3, 300L),
      SeriesData(4, 400L)),
      "foo", listOf(
      SeriesData(1, 100L),
      SeriesData(2, 200L)))
      .inOrder()
  }

  @Test
  fun buildPowerCountersDataDeltaView() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    // The rails 'power.rails.ddr.a' & 'power.rails.ddr.c' are used as they are known to be grouped under "Memory".
    // The 'power.rails.cpu.big' is in a group "CPU Big' by itself.
    val powerRails = listOf(
      CounterModel("power.rails.ddr.a", sortedMapOf(1L to 100.0, 2L to 200.0)),
      CounterModel("power.rails.ddr.c", sortedMapOf(3L to 300.0, 4L to 400.0)),
      CounterModel("power.rails.cpu.big", sortedMapOf(5L to 500.0, 6L to 600.0)))

    val batteryDrain = listOf(
      CounterModel("foo", sortedMapOf(1L to 100.0, 2L to 200.0)),
      CounterModel("bar", sortedMapOf(3L to 300.0, 4L to 400.0)))

    val model = TestModel(processes, emptyMap(), emptyList(), powerRails, batteryDrain, emptyList())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range())
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.powerRailCounters).hasSize(2)
    assertThat(systemTraceData.batteryDrainCounters).hasSize(2)

    assertThat(systemTraceData.powerRailCounters["Memory"]!!.deltaData).containsExactly(
      SeriesData(4, 100L))
      .inOrder()

    assertThat(systemTraceData.powerRailCounters["CPU Big"]!!.deltaData).containsExactly(
      SeriesData(6, 100L))
      .inOrder()

    // In the DELTA view, only the power rail counters have the delta computed,
    // while the battery counters stay in CUMULATIVE/raw view.
    assertThat(systemTraceData.batteryDrainCounters).containsExactly(
      "bar", listOf(
      SeriesData(3, 300L),
      SeriesData(4, 400L)),
      "foo", listOf(
      SeriesData(1, 100L),
      SeriesData(2, 200L)))
      .inOrder()
  }

  @Test
  fun buildBlastBufferQueueCounterData() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf("QueuedBuffer - ViewRootImpl[MainActivity]BLAST#0" to CounterModel("PendingBuffer - ViewRootImpl[MainActivity]BLAST#0",
                                                                                 sortedMapOf(1L to 1.0, 4L to 2.0, 7L to 3.0)))))
    val model = TestModel(processes, emptyMap(), listOf(), listOf(), listOf())
    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range())
    val systemTraceData = capture.systemTraceData!!

    assertThat(systemTraceData.bufferQueueCounterValues).containsExactly(
      SeriesData(1, 1L),
      SeriesData(4, 2L),
      SeriesData(7, 3L)
    ).inOrder()
  }

  @Test
  fun viewRangeOnTimelineIsSet() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    val model = TestModel(processes, emptyMap(), listOf(), listOf(), listOf())

    val builder = SystemTraceCpuCaptureBuilder(model)
    val capture = builder.build(0L, 1, Range(1.0,2.0))
    assertThat(capture.timeline.viewRange.min).isEqualTo(1.0)
    assertThat(capture.timeline.viewRange.max).isEqualTo(2.0)
  }

  @Test
  fun `trace gives right render events for frame`() {
    val mainThread = ThreadModel(1, 1, "main",
                                 listOf(TraceEventModel("${SystemTraceCpuCapture.MAIN_THREAD_EVENT_PREFIX} 42", 0, 500, 500, listOf()),
                                 TraceEventModel("${SystemTraceCpuCapture.MAIN_THREAD_EVENT_PREFIX} 43", 5000, 5500, 500, listOf())),
                                 listOf(), listOf())
    val renderThread = ThreadModel(2, 2, CpuThreadInfo.RENDER_THREAD_NAME,
                                   listOf(TraceEventModel("${SystemTraceCpuCapture.RENDER_THREAD_EVENT_PREFIX} 42", 500, 1500, 1000, listOf()),
                                          TraceEventModel("${SystemTraceCpuCapture.RENDER_THREAD_EVENT_PREFIX} 43", 5500, 6500, 1000, listOf())),
                                   listOf(), listOf())
    val gpuThread = ThreadModel(3, 3, CpuThreadInfo.GPU_THREAD_NAME,
                                listOf(TraceEventModel("${SystemTraceCpuCapture.GPU_THREAD_EVENT_PREFIX} 123", 1500, 2000, 500, listOf())),
                                listOf(), listOf())
    val processes = mapOf(1 to ProcessModel(
      1, "Process",
      mapOf(1 to mainThread, 2 to renderThread, 3 to gpuThread),
      mapOf()
    ))
    val frame1 = AndroidFrameTimelineEvent(42, 42, 0, 1500, 2000,
                                           "layer",
                                           PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                                           PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED,
                                           false, false, 0)
    val frame2 = AndroidFrameTimelineEvent(43, 43, 5000, 6500, 7500,
                                           "layer",
                                           PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                                           PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED,
                                           false, false, 0)
    val model = TestModel(processes, mapOf(), listOf(), listOf(), listOf(), timelineEvents = listOf(frame1, frame2))
    val capture = SystemTraceCpuCaptureBuilder(model).build(0L, 1, Range(0.0, 7000.0))

    capture.frameRenderSequence(frame1).let { (mainEvent, renderEvent, gpuEvent) ->
      assertThat(mainEvent!!.data.nameWithSuffix).isEqualTo("${SystemTraceCpuCapture.MAIN_THREAD_EVENT_PREFIX} 42")
      assertThat(renderEvent!!.data.nameWithSuffix).isEqualTo("${SystemTraceCpuCapture.RENDER_THREAD_EVENT_PREFIX} 42")
      assertThat(gpuEvent!!.data.nameWithSuffix).isEqualTo("${SystemTraceCpuCapture.GPU_THREAD_EVENT_PREFIX} 123")
    }
    capture.frameRenderSequence(frame2).let { (mainEvent, renderEvent, gpuEvent) ->
      assertThat(mainEvent!!.data.nameWithSuffix).isEqualTo("${SystemTraceCpuCapture.MAIN_THREAD_EVENT_PREFIX} 43")
      assertThat(renderEvent!!.data.nameWithSuffix).isEqualTo("${SystemTraceCpuCapture.RENDER_THREAD_EVENT_PREFIX} 43")
      assertThat(gpuEvent).isNull()
    }
  }

  class TestModel(
    private val processes: Map<Int, ProcessModel>,
    private val danglingThreads: Map<Int, ThreadModel>,
    private val cpuCores: List<CpuCoreModel>,
    private val powerRails: List<CounterModel>,
    private val batteryDrain: List<CounterModel>,
    private val timelineEvents: List<AndroidFrameTimelineEvent> = listOf()) : SystemTraceModelAdapter {

    override fun getCaptureStartTimestampUs() = 0L
    override fun getCaptureEndTimestampUs() = 200L
    override fun getProcessById(id: Int) = processes[id]
    override fun getProcesses(): List<ProcessModel> = processes.values.sortedBy { it.id }
    override fun getDanglingThread(tid: Int): ThreadModel? = danglingThreads[tid]
    override fun getCpuCores(): List<CpuCoreModel> = cpuCores

    override fun getSystemTraceTechnology() = TraceType.UNSPECIFIED
    override fun getPowerRails(): List<CounterModel> = powerRails
    override fun getBatteryDrain(): List<CounterModel> = batteryDrain
    override fun isCapturePossibleCorrupted() = false
    override fun getAndroidFrameLayers(): List<TraceProcessor.AndroidFrameEventsResult.Layer> = emptyList()
    override fun getAndroidFrameTimelineEvents(): List<AndroidFrameTimelineEvent> = timelineEvents
  }
}