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

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessor.AndroidFrameEventsResult.FrameEvent
import com.android.tools.profiler.perfetto.proto.TraceProcessor.AndroidFrameEventsResult.Layer
import com.android.tools.profiler.perfetto.proto.TraceProcessor.AndroidFrameEventsResult.Phase
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.CounterModel
import com.android.tools.profilers.cpu.systemtrace.CpuCoreModel
import com.android.tools.profilers.cpu.systemtrace.SchedulingEventModel
import com.android.tools.profilers.cpu.systemtrace.TraceEventModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import perfetto.protos.PerfettoTrace

class TraceProcessorModelTest {

  @Test
  fun addProcessMetadata() {
    val protoBuilder = TraceProcessor.ProcessMetadataResult.newBuilder()
    protoBuilder.addProcess(1, "Process1")
      .addThread(2, "AnotherThreadProcess1")
      .addThread(1, "MainThreadProcess1")
    protoBuilder.addProcess(4, "Process2")
      .addThread(6, "AnotherThreadProcess2")

    val modelBuilder = TraceProcessorModel.Builder()
    modelBuilder.addProcessMetadata(protoBuilder.build())
    val model = modelBuilder.build()

    assertThat(model.getProcesses()).hasSize(2)

    val process1 = model.getProcessById(1)!!
    assertThat(process1.name).isEqualTo("Process1")
    assertThat(process1.getThreads()).hasSize(2)
    assertThat(process1.getThreads().map { it.name }).containsExactly("MainThreadProcess1", "AnotherThreadProcess1").inOrder()
    assertThat(process1.getMainThread()!!.name).isEqualTo("MainThreadProcess1")

    val process2 = model.getProcessById(4)!!
    assertThat(process2.name).isEqualTo("Process2")
    assertThat(process2.getThreads()).hasSize(1)
    assertThat(process2.getThreads().map { it.name }).containsExactly("AnotherThreadProcess2")
    assertThat(process2.getMainThread()).isNull()
  }

  @Test
  fun addTraceEvents() {
    val processProtoBuilder = TraceProcessor.ProcessMetadataResult.newBuilder()
    processProtoBuilder.addProcess(1, "Process1")
      .addThread(2, "AnotherThreadProcess1")
      .addThread(1, "MainThreadProcess1")

    val traceProtoBuilder = TraceProcessor.TraceEventsResult.newBuilder()
    traceProtoBuilder.addThread(1)
      .addEvent(1000, 1000, 2000, "EventA")
      .addEvent(1001, 2000, 5000, "EventA-1", 1000, 1)
      .addEvent(1002, 5000, 1000, "EventA-1-1", 1001, 2)
      .addEvent(1003, 15000, 3000, "EventA-2", 1000, 1)

    val modelBuilder = TraceProcessorModel.Builder()
    modelBuilder.addProcessMetadata(processProtoBuilder.build())
    modelBuilder.addTraceEvents(traceProtoBuilder.build())
    val model = modelBuilder.build()

    val process = model.getProcessById(1)!!
    val thread = process.threadById[1] ?: error("Thread with id = 1 should be present in this process.")
    assertThat(thread.traceEvents).containsExactly(
      // EventA end is 18, because A-2 ends at 18.
      TraceEventModel("EventA", 1, 18, 2, listOf(
        TraceEventModel("EventA-1", 2, 7, 5, listOf(
          TraceEventModel("EventA-1-1", 5, 6, 1, listOf())
        )),
        TraceEventModel("EventA-2", 15, 18, 3, listOf()))))

    assertThat(model.getCaptureStartTimestampUs()).isEqualTo(1)
    assertThat(model.getCaptureEndTimestampUs()).isEqualTo(18)
  }

  @Test
  fun `addTraceEvents - missing thread`() {
    val traceProtoBuilder = TraceProcessor.TraceEventsResult.newBuilder()
    // Just to test that while processing this we don't crash because we can't find the thread referenced.
    traceProtoBuilder.addThread(1)
      .addEvent(10000, 10000, 2000, "EventForMissingThread")

    val modelBuilder = TraceProcessorModel.Builder()
    modelBuilder.addTraceEvents(traceProtoBuilder.build())
    val model = modelBuilder.build()

    assertThat(model.getProcesses()).isEmpty()
  }

  @Test
  fun addSchedulingEvents() {
    val processProtoBuilder = TraceProcessor.ProcessMetadataResult.newBuilder()
    processProtoBuilder.addProcess(1, "Process1")
      .addThread(2, "AnotherThreadProcess1")
      .addThread(1, "MainThreadProcess1")

    val schedProtoBuilder = TraceProcessor.SchedulingEventsResult.newBuilder().setNumCores(4)
    schedProtoBuilder.addSchedulingEvent(1, 1, 1, 1000, 3000,
                                         TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.SLEEPING)
    schedProtoBuilder.addSchedulingEvent(1, 1, 1, 7000, 2000,
                                         TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNABLE)
    schedProtoBuilder.addSchedulingEvent(1, 1, 2, 11000, 2000,
                                         TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNABLE)
    schedProtoBuilder.addSchedulingEvent(1, 2, 2, 2000, 4000,
                                         TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNABLE)
    schedProtoBuilder.addSchedulingEvent(1, 2, 1, 10000, 1000,
                                         TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.SLEEPING)

    val modelBuilder = TraceProcessorModel.Builder()
    modelBuilder.addProcessMetadata(processProtoBuilder.build())
    modelBuilder.addSchedulingEvents(schedProtoBuilder.build())
    val model = modelBuilder.build()

    val process = model.getProcessById(1)!!
    val thread1 = process.threadById[1] ?: error("Thread 1 should be present")
    assertThat(thread1.schedulingEvents).containsExactly(
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 1, 4, 3, 3, 1, 1, 1),
      SchedulingEventModel(ThreadState.SLEEPING_CAPTURED, 4, 7, 3, 3, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 7, 9, 2, 2, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNABLE_CAPTURED, 9, 11, 2, 2, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 11, 13, 2, 2, 1, 1, 2))
      .inOrder()
    val thread2 = process.threadById[2] ?: error("Thread 2 should be present")
    assertThat(thread2.schedulingEvents).containsExactly(
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 2, 6, 4, 4, 1, 2, 2),
      SchedulingEventModel(ThreadState.RUNNABLE_CAPTURED, 6, 10, 4, 4, 1, 2, 2),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 10, 11, 1, 1, 1, 2, 1))
      .inOrder()

    val cpus = model.getCpuCores()
    assertThat(cpus).hasSize(4)
    assertThat(cpus[0].id).isEqualTo(0)
    assertThat(cpus[0].schedulingEvents).isEmpty()

    assertThat(cpus[1].id).isEqualTo(1)
    assertThat(cpus[1].schedulingEvents).containsExactly(
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 1, 4, 3, 3, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 7, 9, 2, 2, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 10, 11, 1, 1, 1, 2, 1))
      .inOrder()

    assertThat(cpus[2].id).isEqualTo(2)
    assertThat(cpus[2].schedulingEvents).containsExactly(
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 2, 6, 4, 4, 1, 2, 2),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 11, 13, 2, 2, 1, 1, 2))
      .inOrder()

    assertThat(cpus[3].id).isEqualTo(3)
    assertThat(cpus[3].schedulingEvents).isEmpty()

    assertThat(model.getCaptureStartTimestampUs()).isEqualTo(1)
    assertThat(model.getCaptureEndTimestampUs()).isEqualTo(13)
  }

  @Test
  fun addCpuCounters() {
    val cpuCounters = TraceProcessor.CpuCoreCountersResult.newBuilder()
      .setNumCores(2)
      .addCountersPerCore(
        TraceProcessor.CpuCoreCountersResult.CountersPerCore.newBuilder()
          .setCpu(0)
          .addCounter(TraceProcessor.Counter.newBuilder()
                        .setName("cpufreq")
                        .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(1000).setValue(0.0))
                        .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(2000).setValue(1000.0))
                        .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(3000).setValue(2000.0)))
          .addCounter(TraceProcessor.Counter.newBuilder()
                        .setName("cpuidle")
                        .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(1500).setValue(0.0))
                        .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(2500).setValue(4294967295.0))
                        .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(3500).setValue(0.0))))
      .addCountersPerCore(TraceProcessor.CpuCoreCountersResult.CountersPerCore.newBuilder().setCpu(1))
      .build()

    val model = TraceProcessorModel.Builder().apply {
      addCpuCounters(cpuCounters)
    }.build()

    assertThat(model.getCpuCores()).containsExactly(
      CpuCoreModel(0, listOf(), mapOf(
        "cpufreq" to CounterModel("cpufreq", sortedMapOf(1L to 0.0, 2L to 1000.0, 3L to 2000.0)),
        "cpuidle" to CounterModel("cpuidle", sortedMapOf(1L to 0.0, 2L to 4294967295.0, 3L to 0.0))
      )),
      CpuCoreModel(1, listOf(), mapOf()))
  }

  @Test
  fun addCounters() {
    val processProtoBuilder = TraceProcessor.ProcessMetadataResult.newBuilder()
    processProtoBuilder.addProcess(1, "Process1").addThread(1, "MainThreadProcess1")

    val counterProtoBuilder = TraceProcessor.ProcessCountersResult.newBuilder().setProcessId(1)
    val p1CounterA = counterProtoBuilder.addCounterBuilder().setName("CounterA")
    p1CounterA.addValueBuilder().setTimestampNanoseconds(1000).setValue(0.0)
    p1CounterA.addValueBuilder().setTimestampNanoseconds(2000).setValue(1.0)
    p1CounterA.addValueBuilder().setTimestampNanoseconds(3000).setValue(0.0)

    val p1CounterZ = counterProtoBuilder.addCounterBuilder().setName("CounterZ")
    p1CounterZ.addValueBuilder().setTimestampNanoseconds(1500).setValue(100.0)
    p1CounterZ.addValueBuilder().setTimestampNanoseconds(2500).setValue(50.0)
    p1CounterZ.addValueBuilder().setTimestampNanoseconds(3500).setValue(100.0)

    val modelBuilder = TraceProcessorModel.Builder()
    modelBuilder.addProcessMetadata(processProtoBuilder.build())
    modelBuilder.addProcessCounters(counterProtoBuilder.build())
    val model = modelBuilder.build()

    val process = model.getProcessById(1)!!
    val counters = process.counterByName
    assertThat(counters).hasSize(2)
    val counterA = counters["CounterA"] ?: error("CounterA should be present.")
    assertThat(counterA.name).isEqualTo("CounterA")
    assertThat(counterA.valuesByTimestampUs).containsExactly(1L, 0.0, 2L, 1.0, 3L, 0.0).inOrder()
    val counterZ = counters["CounterZ"] ?: error("CounterZ should be present.")
    assertThat(counterZ.name).isEqualTo("CounterZ")
    assertThat(counterZ.valuesByTimestampUs).containsExactly(1L, 100.0, 2L, 50.0, 3L, 100.0).inOrder()
  }

  @Test
  fun addAndroidFrameLayers() {
    val layer = Layer.newBuilder()
      .setLayerName("foobar")
      .addPhase(Phase.newBuilder()
                  .setPhaseName("Display")
                  .addFrameEvent(FrameEvent.newBuilder().setId(1)))
      .addPhase(Phase.newBuilder()
                  .setPhaseName("GPU")
                  .addFrameEvent(FrameEvent.newBuilder().setId(2)))
      .build()
    val frameEventResult = TraceProcessor.AndroidFrameEventsResult.newBuilder()
      .addLayer(layer)
      .build()
    val model = TraceProcessorModel.Builder().apply {
      addAndroidFrameEvents(frameEventResult)
    }.build()
    assertThat(model.getAndroidFrameLayers()).containsExactly(layer)
  }

  @Test
  fun addAndroidFrameTimelineEvents() {
    val frameTimelineResult = TraceProcessor.AndroidFrameTimelineResult.newBuilder()
      .addExpectedSlice(TraceProcessor.AndroidFrameTimelineResult.ExpectedSlice.newBuilder()
                          .setDisplayFrameToken(1)
                          .setSurfaceFrameToken(101)
                          .setTimestampNanoseconds(1000)
                          .setDurationNanoseconds(1000))
      .addExpectedSlice(TraceProcessor.AndroidFrameTimelineResult.ExpectedSlice.newBuilder()
                          .setDisplayFrameToken(4)
                          .setSurfaceFrameToken(104)
                          .setTimestampNanoseconds(7000)
                          .setDurationNanoseconds(1000))
      .addExpectedSlice(TraceProcessor.AndroidFrameTimelineResult.ExpectedSlice.newBuilder()
                          .setDisplayFrameToken(3)
                          .setSurfaceFrameToken(103)
                          .setTimestampNanoseconds(5000)
                          .setDurationNanoseconds(1000))
      .addExpectedSlice(TraceProcessor.AndroidFrameTimelineResult.ExpectedSlice.newBuilder()
                          .setDisplayFrameToken(2)
                          .setSurfaceFrameToken(102)
                          .setTimestampNanoseconds(3000)
                          .setDurationNanoseconds(1000))
      .addActualSlice(TraceProcessor.AndroidFrameTimelineResult.ActualSlice.newBuilder()
                        .setDisplayFrameToken(2)
                        .setSurfaceFrameToken(102)
                        .setTimestampNanoseconds(3000)
                        .setDurationNanoseconds(2000)
                        .setLayerName("foo")
                        .setPresentType("Late Present")
                        .setJankType("App Deadline Missed, SurfaceFlinger CPU Deadline Missed")
                        .setOnTimeFinish(false)
                        .setGpuComposition(true)
                        .setLayoutDepth(1))
      .addActualSlice(TraceProcessor.AndroidFrameTimelineResult.ActualSlice.newBuilder()
                        .setDisplayFrameToken(3)
                        .setSurfaceFrameToken(103)
                        .setTimestampNanoseconds(5000)
                        .setDurationNanoseconds(1000)
                        .setLayerName("foo")
                        .setPresentType("Early Present")
                        .setJankType("Buffer Stuffing, SurfaceFlinger GPU Deadline Missed")
                        .setOnTimeFinish(true)
                        .setGpuComposition(true)
                        .setLayoutDepth(2))
      .addActualSlice(TraceProcessor.AndroidFrameTimelineResult.ActualSlice.newBuilder()
                        .setDisplayFrameToken(4)
                        .setSurfaceFrameToken(104)
                        .setTimestampNanoseconds(7000)
                        .setDurationNanoseconds(3000)
                        .setLayerName("foo")
                        .setPresentType("Dropped Frame")
                        .setJankType("Unknown Jank")
                        .setOnTimeFinish(true)
                        .setGpuComposition(false)
                        .setLayoutDepth(0))
      .addActualSlice(TraceProcessor.AndroidFrameTimelineResult.ActualSlice.newBuilder()
                        .setDisplayFrameToken(1)
                        .setSurfaceFrameToken(101)
                        .setTimestampNanoseconds(1000)
                        .setDurationNanoseconds(1000)
                        .setLayerName("foo")
                        .setPresentType("On-time Present")
                        .setJankType("None")
                        .setOnTimeFinish(true)
                        .setGpuComposition(true)
                        .setLayoutDepth(0))
      .build()
    val model = TraceProcessorModel.Builder().apply {
      addAndroidFrameTimelineEvents(frameTimelineResult)
    }.build()
    assertThat(model.getAndroidFrameTimelineEvents()).containsExactly(
      AndroidFrameTimelineEvent(1, 101, 1, 2, 2, "foo", PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_ON_TIME,
                                PerfettoTrace.FrameTimelineEvent.JankType.JANK_NONE,
                                onTimeFinish = true, gpuComposition = true, layoutDepth = 0),
      AndroidFrameTimelineEvent(2, 102, 3, 4, 5, "foo", PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                                PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED,
                                onTimeFinish = false, gpuComposition = true, layoutDepth = 1),
      AndroidFrameTimelineEvent(3, 103, 5, 6, 6, "foo", PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_EARLY,
                                PerfettoTrace.FrameTimelineEvent.JankType.JANK_BUFFER_STUFFING,
                                onTimeFinish = true, gpuComposition = true, layoutDepth = 2),
      AndroidFrameTimelineEvent(4, 104, 7, 8, 10, "foo", PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_DROPPED,
                                PerfettoTrace.FrameTimelineEvent.JankType.JANK_UNKNOWN,
                                onTimeFinish = true, gpuComposition = false, layoutDepth = 0),
    ).inOrder()
  }

  @Test
  fun addPowerCounters() {
    val powerCounters = TraceProcessor.PowerCounterTracksResult.newBuilder()
      .addCounter(
        TraceProcessor.Counter.newBuilder()
          .setName("power.rails.1")
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(1000).setValue(100.0))
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(2000).setValue(200.0))
      )
      .addCounter(
        TraceProcessor.Counter.newBuilder()
          .setName("power.rails.2")
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(1000).setValue(100.0))
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(2000).setValue(200.0))
      )
      .addCounter(
        TraceProcessor.Counter.newBuilder()
          .setName("batt.1")
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(1000).setValue(100.0))
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(2000).setValue(200.0))
      )
      .addCounter(
        TraceProcessor.Counter.newBuilder()
          .setName("batt.2")
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(1000).setValue(100.0))
          .addValue(TraceProcessor.CounterValue.newBuilder().setTimestampNanoseconds(2000).setValue(200.0))
      )
      .build()


    val model = TraceProcessorModel.Builder().apply {
      addPowerCounters(powerCounters)
    }.build()

    assertThat(model.getPowerRails()).containsExactly(
      CounterModel("power.rails.1", sortedMapOf(1L to 100.0, 2L to 200.0)),
      CounterModel("power.rails.2", sortedMapOf(1L to 100.0, 2L to 200.0)))

    assertThat(model.getBatteryDrain()).containsExactly(
      CounterModel("batt.1", sortedMapOf(1L to 100.0, 2L to 200.0)),
      CounterModel("batt.2", sortedMapOf(1L to 100.0, 2L to 200.0)))
  }

  @Test
  fun `grouping layers by phase adjusts depths`() {
    fun<O,B> constructorOf(builder: () -> B, build: B.() -> O): (B.() -> Unit) -> O = { builder().apply(it).build() }
    val layer = constructorOf(Layer::newBuilder, Layer.Builder::build)
    val phase = constructorOf(Phase::newBuilder, Phase.Builder::build)
    val frame = constructorOf(FrameEvent::newBuilder, FrameEvent.Builder::build)
    val displayPhase = phase {
      phaseName = "Display"
      addFrameEvent(frame { frameNumber = 1; depth = 0 })
      addFrameEvent(frame { frameNumber = 2; depth = 1 })
    }
    val layer1 = layer {
      addPhase(phase {
        phaseName = "Application"
        addFrameEvent(frame { frameNumber = 1; depth = 0 })
        addFrameEvent(frame { frameNumber = 2; depth = 1 })
      })
      addPhase(displayPhase)
    }
    val layer2 = layer {
      addPhase(phase {
        phaseName = "Application"
        addFrameEvent(frame { frameNumber = 3; depth = 0 })
        addFrameEvent(frame { frameNumber = 4; depth = 1 })
      })
      addPhase(displayPhase)
    }
    val layer3 = layer {
      addPhase(phase {
        phaseName = "Application"
        addFrameEvent(frame { frameNumber = 5; depth = 0 })
        addFrameEvent(frame { frameNumber = 6; depth = 1 })
      })
      addPhase(displayPhase)
    }

    assertThat(listOf(layer1, layer2, layer3).groupedByPhase()).isEqualTo(listOf(
      phase {
        phaseName = "Application"
        addFrameEvent(frame { frameNumber = 1; depth = 0 })
        addFrameEvent(frame { frameNumber = 2; depth = 1 })
        addFrameEvent(frame { frameNumber = 3; depth = 2 })
        addFrameEvent(frame { frameNumber = 4; depth = 3 })
        addFrameEvent(frame { frameNumber = 5; depth = 4 })
        addFrameEvent(frame { frameNumber = 6; depth = 5 })
      },
      displayPhase
    ))
  }

  private fun TraceProcessor.ProcessMetadataResult.Builder.addProcess(id: Long, name: String)
    : TraceProcessor.ProcessMetadataResult.ProcessMetadata.Builder {
    return this.addProcessBuilder().setId(id).setName(name)
  }

  private fun TraceProcessor.ProcessMetadataResult.ProcessMetadata.Builder.addThread(id: Long, name: String)
    : TraceProcessor.ProcessMetadataResult.ProcessMetadata.Builder {
    return this.addThread(TraceProcessor.ProcessMetadataResult.ThreadMetadata.newBuilder().setId(id).setName(name))
  }

  private fun TraceProcessor.TraceEventsResult.Builder.addThread(id: Long): TraceProcessor.TraceEventsResult.ThreadTraceEvents.Builder {
    return this.addThreadBuilder().setThreadId(id)
  }

  private fun TraceProcessor.TraceEventsResult.ThreadTraceEvents.Builder.addEvent(id: Long, tsNs: Long, durNs: Long, name: String,
                                                                                  parentId: Long = 0, depth: Int = 0)
    : TraceProcessor.TraceEventsResult.ThreadTraceEvents.Builder {
    this.addTraceEventBuilder()
      .setId(id)
      .setTimestampNanoseconds(tsNs)
      .setDurationNanoseconds(durNs)
      .setName(name)
      .setParentId(parentId)
      .setDepth(depth)
    return this
  }

  private fun TraceProcessor.SchedulingEventsResult.Builder.addSchedulingEvent(
    processId: Long, threadId: Long, cpu: Int, tsNs: Long, durNs: Long,
    endState: TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState) {
    this.addSchedEventBuilder()
      .setProcessId(processId)
      .setThreadId(threadId)
      .setCpu(cpu)
      .setTimestampNanoseconds(tsNs)
      .setDurationNanoseconds(durNs)
      .setEndState(endState)
  }

}