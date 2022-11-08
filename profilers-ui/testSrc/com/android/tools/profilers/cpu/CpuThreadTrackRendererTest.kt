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

import com.android.testutils.MockitoKt.whenever
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
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.analysis.CaptureNodeAnalysisModel
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JComponent

class CpuThreadTrackRendererTest {
  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)
  private val ideProfilerComponents = FakeIdeProfilerComponents()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("CpuThreadTrackRendererTest", FakeCpuService(), FakeProfilerService(timer), transportService,
                                    FakeMemoryService(), FakeEventService())

  @get:Rule
  val applicationRule = ApplicationRule()

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
      whenever(getThreadStatesForThread(1)).thenReturn(listOf())
    }
    val fakeTimeline = DefaultTimeline()
    val mockCapture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(range).thenReturn(Range())
      whenever(type).thenReturn(TraceType.ATRACE)
      whenever(getCaptureNode(1)).thenReturn(captureNode)
      whenever(systemTraceData).thenReturn(sysTraceData)
      whenever(timeline).thenReturn(fakeTimeline)
    }
    val threadTrackModel = TrackModel.newBuilder(
      CpuThreadTrackModel(
        mockCapture,
        threadInfo,
        fakeTimeline,
        multiSelectionModel,
        Utils::runOnUi
      ),
      ProfilerTrackRendererType.CPU_THREAD, "Foo").build()
    val renderer = CpuThreadTrackRenderer(profilersView) { false }
    val componentWithOverlay = renderer.render(threadTrackModel).getComponent(0) as JComponent
    val component = componentWithOverlay.components[1] as JComponent
    assertThat(component.componentCount).isEqualTo(2)
    assertThat(component.components[0]).isInstanceOf(StateChart::class.java)
    assertThat(component.components[1]).isInstanceOf(HTreeChart::class.java)

    // Verify trace event chart selection is updated.
    val traceEventChart = component.components[1] as HTreeChart<CaptureNode>
    assertThat(traceEventChart.selectedNode).isNull()
    multiSelectionModel.setSelection(captureNode, setOf(CaptureNodeAnalysisModel(captureNode, mockCapture, Utils::runOnUi)))
    assertThat(traceEventChart.selectedNode).isSameAs(captureNode)
    multiSelectionModel.clearSelection()
    assertThat(traceEventChart.selectedNode).isNull()
  }

  @Test
  fun noThreadStateChartForImportedTrace() {
    // Mock an imported ART trace
    val mockCapture = Mockito.mock(CpuCapture::class.java)
    whenever(mockCapture.range).thenReturn(Range())
    whenever(mockCapture.type).thenReturn(TraceType.ART)
    whenever(mockCapture.getCaptureNode(1)).thenReturn(CaptureNode(StubCaptureNodeModel()))
    val threadTrackModel = TrackModel.newBuilder(
      CpuThreadTrackModel(
        mockCapture,
        CpuThreadInfo(1, "Thread-1"),
        DefaultTimeline(),
        MultiSelectionModel(),
        Utils::runOnUi
      ),
      ProfilerTrackRendererType.CPU_THREAD, "Foo").build()
    val component = CpuThreadTrackRenderer(profilersView, {false}).render(threadTrackModel)
    assertThat(component.componentCount).isEqualTo(1)
    assertThat(component.components[0]).isInstanceOf(HTreeChart::class.java)
  }

  @Test
  fun supportsCodeNavigationForArt() {
    // Mock a recorded ART trace.
    val mockCapture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(range).thenReturn(Range())
      whenever(type).thenReturn(TraceType.ART)
    }
    val threadTrackModel = TrackModel.newBuilder(
      CpuThreadTrackModel(
        mockCapture,
        CpuThreadInfo(1, "Thread-1"),
        DefaultTimeline(),
        MultiSelectionModel(),
        Utils::runOnUi
      ),
      ProfilerTrackRendererType.CPU_THREAD, "Foo").build()
    val renderer = CpuThreadTrackRenderer(profilersView, {false})
    val component = renderer.render(threadTrackModel)
    val callChart = TreeWalker(component).descendants().filterIsInstance<HTreeChart<*>>().first()
    assertThat(ideProfilerComponents.getCodeLocationSupplier(callChart)).isNotNull()
  }
}