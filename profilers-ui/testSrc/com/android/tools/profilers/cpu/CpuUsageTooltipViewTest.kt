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
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel

class CpuUsageTooltipViewTest {
  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()
  private lateinit var cpuStage: CpuProfilerStage
  private lateinit var usageTooltipView: FakeCpuUsageTooltipView
  @Rule
  @JvmField
  val myGrpcChannel = FakeGrpcChannel("CpuUsageTooltipViewTest", cpuService,
                                      FakeTransportService(timer), FakeProfilerService(timer))

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.name), FakeIdeProfilerServices(), timer)
    cpuStage = CpuProfilerStage(profilers)
    timer.tick(TimeUnit.SECONDS.toNanos(1))
    profilers.stage = cpuStage
    val view = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView: CpuProfilerStageView = view.stageView as CpuProfilerStageView
    val usageTooltip = CpuUsageTooltip(cpuStage)
    usageTooltipView = FakeCpuUsageTooltipView(stageView, usageTooltip)
    cpuStage.tooltip = usageTooltip
    val tooltipTime = TimeUnit.SECONDS.toMicros(1)
    stageView.timeline.apply {
      dataRange.set(0.0, TimeUnit.SECONDS.toMicros(5).toDouble())
      tooltipRange.set(tooltipTime.toDouble(), tooltipTime.toDouble())
      viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
    }
    cpuService.addTraceInfo(Cpu.CpuTraceInfo.newBuilder()
                              .setTraceId(1)
                              .setTraceType(Cpu.CpuTraceType.ATRACE)
                              .setFromTimestamp(TimeUnit.SECONDS.toNanos(2))
                              .setToTimestamp(TimeUnit.SECONDS.toNanos(4))
                              .addTids(3).build())
    cpuService.addTraceInfo(Cpu.CpuTraceInfo.newBuilder()
                              .setTraceId(2)
                              .setTraceType(Cpu.CpuTraceType.SIMPLEPERF)
                              .setFromTimestamp(TimeUnit.SECONDS.toNanos(5))
                              .setToTimestamp(TimeUnit.SECONDS.toNanos(7))
                              .addTids(3).build())

  }

  @Test
  fun textUpdateOnThreadChange() {
    var labels = TreeWalker(usageTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(2) // time, details unavailable
    assertThat(labels[0].text).isEqualTo("00:01.000")
    assertThat(labels[1].text).isEqualTo("Selection Unavailable")

    cpuStage.studioProfilers.timeline.tooltipRange.set(TimeUnit.SECONDS.toMicros(3).toDouble(),
                                                       TimeUnit.SECONDS.toMicros(3).toDouble())
    labels = TreeWalker(usageTooltipView.tooltipPanel).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(2) // time, name, state, details unavailable
    assertThat(labels[0].text).isEqualTo("00:03.000")
    assertThat(labels[1].text).isEqualTo(ProfilingTechnology.ATRACE.getName())
  }

  private class FakeCpuUsageTooltipView(
    parent: CpuProfilerStageView,
    tooltip: CpuUsageTooltip)
    : CpuUsageTooltipView(parent, tooltip) {
    val tooltipPanel: JComponent = createComponent()
  }
}
