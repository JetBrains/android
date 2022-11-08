/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel

class CpuProfilerStageCpuUsageTooltipViewTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  private lateinit var cpuStage: CpuProfilerStage
  private lateinit var usageTooltipView: FakeCpuUsageTooltipView

  @Rule
  @JvmField
  val myGrpcChannel = FakeGrpcChannel("CpuUsageTooltipViewTest", transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  @Before
  fun setUp() {
    val profilerServices = FakeIdeProfilerServices().also {
      it.enableEventsPipeline(true)
    }
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), profilerServices, timer)
    cpuStage = CpuProfilerStage(profilers)
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    profilers.stage = cpuStage
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView: CpuProfilerStageView = view.stageView as CpuProfilerStageView
    val usageTooltip = CpuProfilerStageCpuUsageTooltip(cpuStage)
    usageTooltipView = FakeCpuUsageTooltipView(stageView, usageTooltip)
    cpuStage.tooltip = usageTooltip
    val tooltipTime = TimeUnit.SECONDS.toMicros(1)
    stageView.stage.timeline.apply {
      dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
      tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
      viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
    }
    addTraceInfo(1, 2, 4, TraceType.ATRACE)
    addTraceInfo(2, 5, 7, TraceType.SIMPLEPERF)
  }

  @Test
  fun textUpdateOnThreadChange() {
    var labels = TreeWalker(usageTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(2) // time, details unavailable
    assertThat(labels[0].text).isEqualTo("00:01.000")
    assertThat(labels[1].text).isEqualTo("Selection Unavailable")

    cpuStage.timeline.tooltipRange.set(TimeUnit.SECONDS.toMicros(3).toDouble(),
                                       TimeUnit.SECONDS.toMicros(3).toDouble())
    labels = TreeWalker(usageTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(2) // time, name, state, details unavailable
    assertThat(labels[0].text).isEqualTo("00:03.000")
    assertThat(labels[1].text).isEqualTo(ProfilingTechnology.SYSTEM_TRACE.getName())
  }


  private fun addTraceInfo(traceId: Long, startTimeSec: Long, endTimeSec: Long, traceType: TraceType) {
    // TODO (b/258542374): Remove TRACE_TYPE_MAP, UserOptions will have to be removed as well.
    val configuration = Trace.TraceConfiguration.newBuilder().setUserOptions(
      Trace.UserOptions.newBuilder().setTraceType(ProfilingConfiguration.TRACE_TYPE_MAP[traceType]))

    TraceConfigOptionsUtils.addDefaultTraceOptions(configuration, traceType)

    val traceInfo: Cpu.CpuTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(TimeUnit.SECONDS.toNanos(startTimeSec))
      .setToTimestamp(TimeUnit.SECONDS.toNanos(endTimeSec))
      .setConfiguration(configuration).build()
    val traceEventBuilder = Common.Event.newBuilder()
      .setGroupId(traceId)
      .setPid(FakeTransportService.FAKE_PROCESS.pid)
      .setKind(Common.Event.Kind.CPU_TRACE)
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      traceEventBuilder.setTimestamp(TimeUnit.SECONDS.toNanos(startTimeSec)).setCpuTrace(
                                        Cpu.CpuTraceData.newBuilder().setTraceStarted(
                                          Cpu.CpuTraceData.TraceStarted.newBuilder().setTraceInfo(traceInfo))).build())
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      traceEventBuilder.setTimestamp(TimeUnit.SECONDS.toNanos(endTimeSec)).setCpuTrace(
                                        Cpu.CpuTraceData.newBuilder().setTraceEnded(
                                          Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(traceInfo))).build())
  }

  private class FakeCpuUsageTooltipView(
    parent: CpuProfilerStageView,
    tooltip: CpuProfilerStageCpuUsageTooltip)
    : CpuProfilerStageCpuUsageTooltipView(parent, tooltip) {
    val tooltipPanel: JComponent = createComponent()
  }
}
