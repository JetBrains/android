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
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel
import com.android.tools.profilers.cpu.analysis.CpuAnalysisTabModel.Type
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class CpuThreadTrackModelTest {
  private lateinit var myProfilers: StudioProfilers

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  val grpcChannel = FakeGrpcChannel("CpuCaptureParserTest", transportService)

  @Before
  fun setUp() {
    val ideServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
  }

  @Test
  fun noThreadStatesFromArtTrace() {
    val capture = CpuProfilerTestUtils.getValidCapture(myProfilers)
    val threadTrackModel = CpuThreadTrackModel(capture, CpuThreadInfo(516, "Foo"), DefaultTimeline(), MultiSelectionModel(),
                                               Utils::runOnUi)
    assertThat(threadTrackModel.threadStateChartModel.series).isEmpty()
  }

  @Test
  fun analysisTabs() {
    val capture = Mockito.mock(CpuCapture::class.java).apply {
      whenever(this.range).thenReturn(Range())
    }
    val threadTrackModel = CpuThreadTrackModel(capture, CpuThreadInfo(1, "Foo"), DefaultTimeline(), MultiSelectionModel(),
                                               Utils::runOnUi)
    val analysisTabModels = threadTrackModel.analysisModel.tabModels.map(CpuAnalysisTabModel<*>::getTabType).toSet()
    assertThat(analysisTabModels).containsExactly(Type.SUMMARY, Type.FLAME_CHART, Type.TOP_DOWN, Type.BOTTOM_UP, Type.EVENTS)
  }

  @Test
  fun testNameToNodes() {
    val capture = CpuProfilerTestUtils.getValidCapture(myProfilers)
    val threadTrackModel = CpuThreadTrackModel(capture, CpuThreadInfo(516, "Foo"), DefaultTimeline(), MultiSelectionModel(),
                                               Utils::runOnUi)
    val node = threadTrackModel.callChartModel.node

    val nameToNodes = CpuThreadTrackModel.getNameToNodesMapping(node)
    // Make sure the size of the constructed map is correct.
    assertThat(nameToNodes.size).isEqualTo(30)
    assertThat(nameToNodes.containsKey("main"))
    // Check one element to make sure it has the right data.
    assertThat(nameToNodes["main"]!!.size).isEqualTo(1)
    assertThat(nameToNodes["main"]!!.get(0).data.fullName).isEqualTo("main")
  }
}