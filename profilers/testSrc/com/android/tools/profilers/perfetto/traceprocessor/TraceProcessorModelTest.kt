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
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.cpu.systemtrace.CounterModel
import com.android.tools.profilers.cpu.systemtrace.CpuCoreModel
import com.android.tools.profilers.cpu.systemtrace.SchedulingEventModel
import com.android.tools.profilers.cpu.systemtrace.TraceEventModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
    schedProtoBuilder.addSchedulingEvent(1, 1, 1, 7000, 2000, TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNING)
    schedProtoBuilder.addSchedulingEvent(1, 1, 2, 11000, 2000,
                                         TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNING)
    schedProtoBuilder.addSchedulingEvent(1, 2, 2, 2000, 4000, TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNING)
    schedProtoBuilder.addSchedulingEvent(1, 2, 1, 10000, 1000,
                                         TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.SLEEPING)

    val modelBuilder = TraceProcessorModel.Builder()
    modelBuilder.addProcessMetadata(processProtoBuilder.build())
    modelBuilder.addSchedulingEvents(schedProtoBuilder.build())
    val model = modelBuilder.build()

    val process = model.getProcessById(1)!!
    val thread1 = process.threadById[1] ?: error("Thread 1 should be present")
    assertThat(thread1.schedulingEvents).containsExactly(
      SchedulingEventModel(ThreadState.SLEEPING_CAPTURED, 1, 4, 3, 3, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 7, 9, 2, 2, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 11, 13, 2, 2, 1, 1, 2))
      .inOrder()
    val thread2 = process.threadById[2] ?: error("Thread 2 should be present")
    assertThat(thread2.schedulingEvents).containsExactly(
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 2, 6, 4, 4, 1, 2, 2),
      SchedulingEventModel(ThreadState.SLEEPING_CAPTURED, 10, 11, 1, 1, 1, 2, 1))
      .inOrder()

    val cpus = model.getCpuCores()
    assertThat(cpus).hasSize(4)
    assertThat(cpus[0].id).isEqualTo(0)
    assertThat(cpus[0].schedulingEvents).isEmpty()

    assertThat(cpus[1].id).isEqualTo(1)
    assertThat(cpus[1].schedulingEvents).hasSize(3)
    assertThat(cpus[1].schedulingEvents).containsExactly(
      SchedulingEventModel(ThreadState.SLEEPING_CAPTURED, 1, 4, 3, 3, 1, 1, 1),
      SchedulingEventModel(ThreadState.RUNNING_CAPTURED, 7, 9, 2, 2, 1, 1, 1),
      SchedulingEventModel(ThreadState.SLEEPING_CAPTURED, 10, 11, 1, 1, 1, 2, 1))
      .inOrder()

    assertThat(cpus[2].id).isEqualTo(2)
    assertThat(cpus[2].schedulingEvents).hasSize(2)
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
    val layer = TraceProcessor.AndroidFrameEventsResult.Layer.newBuilder()
      .setLayerName("foobar")
      .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                  .setPhaseName("Display")
                  .addFrameEvent(TraceProcessor.AndroidFrameEventsResult.FrameEvent.newBuilder().setId(1)))
      .addPhase(TraceProcessor.AndroidFrameEventsResult.Phase.newBuilder()
                  .setPhaseName("GPU")
                  .addFrameEvent(TraceProcessor.AndroidFrameEventsResult.FrameEvent.newBuilder().setId(2)))
      .build()
    val frameEventResult = TraceProcessor.AndroidFrameEventsResult.newBuilder()
      .addLayer(layer)
      .build()
    val model = TraceProcessorModel.Builder().apply {
      addAndroidFrameEvents(frameEventResult)
    }.build()
    assertThat(model.getAndroidFrameLayers()).containsExactly(layer)
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
    state: TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState) {
    this.addSchedEventBuilder()
      .setProcessId(processId)
      .setThreadId(threadId)
      .setCpu(cpu)
      .setTimestampNanoseconds(tsNs)
      .setDurationNanoseconds(durNs)
      .setState(state)
  }

}