/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.chart.statechart.StateChart
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.analysis.CaptureNodeAnalysisModel
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class CpuThreadTrackRendererTest {
  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)
  private val ideProfilerComponents = FakeIdeProfilerComponents()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuThreadTrackRendererTest", FakeCpuService(), FakeProfilerService(timer), transportService,
                                    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private lateinit var profilers: StudioProfilers
  private lateinit var profilersView: StudioProfilersView

  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    profilersView = StudioProfilersView(profilers, ideProfilerComponents)
  }

  @Test
  fun renderComponentsForThreadTrack() {
    // Mock a recorded Atrace.
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val threadInfo = CpuThreadInfo(1, "Thread-1")
    val multiSelectionModel = MultiSelectionModel<CpuAnalyzable<*>>()
    val captureNode = CaptureNode(StubCaptureNodeModel())
    val sysTraceData = Mockito.mock(CpuSystemTraceData::class.java).apply {
      Mockito.`when`(getThreadStatesForThread(1)).thenReturn(listOf())
    }
    val mockCapture = Mockito.mock(CpuCapture::class.java).apply {
      Mockito.`when`(range).thenReturn(Range())
      Mockito.`when`(type).thenReturn(Cpu.CpuTraceType.ATRACE)
      Mockito.`when`(getCaptureNode(1)).thenReturn(captureNode)
      Mockito.`when`(systemTraceData).thenReturn(sysTraceData)
    }
    val threadTrackModel = TrackModel.newBuilder(
      CpuThreadTrackModel(
        mockCapture,
        threadInfo,
        DefaultTimeline(),
        multiSelectionModel
      ),
      ProfilerTrackRendererType.CPU_THREAD, "Foo").build()
    val renderer = CpuThreadTrackRenderer(profilersView)
    val component = renderer.render(threadTrackModel)
    assertThat(component.componentCount).isEqualTo(2)
    assertThat(component.components[0]).isInstanceOf(StateChart::class.java)
    assertThat(component.components[1]).isInstanceOf(HTreeChart::class.java)

    // Verify trace event chart selection is updated.
    val traceEventChart = component.components[1] as HTreeChart<CaptureNode>
    assertThat(traceEventChart.selectedNode).isNull()
    multiSelectionModel.setSelection(setOf(CaptureNodeAnalysisModel(captureNode, mockCapture)))
    assertThat(traceEventChart.selectedNode).isSameAs(captureNode)
    multiSelectionModel.clearSelection()
    assertThat(traceEventChart.selectedNode).isNull()
  }

  @Test
  fun noThreadStateChartForImportedTrace() {
    // Mock an imported ART trace
    val mockCapture = Mockito.mock(CpuCapture::class.java)
    Mockito.`when`(mockCapture.range).thenReturn(Range())
    Mockito.`when`(mockCapture.type).thenReturn(Cpu.CpuTraceType.ART)
    Mockito.`when`(mockCapture.getCaptureNode(1)).thenReturn(CaptureNode(StubCaptureNodeModel()))
    val threadTrackModel = TrackModel.newBuilder(
      CpuThreadTrackModel(
        mockCapture,
        CpuThreadInfo(1, "Thread-1"),
        DefaultTimeline(),
        MultiSelectionModel()
      ),
      ProfilerTrackRendererType.CPU_THREAD, "Foo").build()
    val component = CpuThreadTrackRenderer(profilersView).render(threadTrackModel)
    assertThat(component.componentCount).isEqualTo(1)
    assertThat(component.components[0]).isInstanceOf(HTreeChart::class.java)
  }

  @Test
  fun supportsCodeNavigationForArt() {
    // Mock a recorded ART trace.
    val mockCapture = Mockito.mock(CpuCapture::class.java).apply {
      Mockito.`when`(range).thenReturn(Range())
      Mockito.`when`(type).thenReturn(Cpu.CpuTraceType.ART)
    }
    val threadTrackModel = TrackModel.newBuilder(
      CpuThreadTrackModel(
        mockCapture,
        CpuThreadInfo(1, "Thread-1"),
        DefaultTimeline(),
        MultiSelectionModel()
      ),
      ProfilerTrackRendererType.CPU_THREAD, "Foo").build()
    val renderer = CpuThreadTrackRenderer(profilersView)
    val component = renderer.render(threadTrackModel)
    val callChart = TreeWalker(component).descendants().filterIsInstance<HTreeChart<*>>().first()
    assertThat(ideProfilerComponents.getCodeLocationSupplier(callChart)).isNotNull()
  }
}