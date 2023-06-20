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

import com.android.tools.adtui.chart.statechart.StateChart
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.systemtrace.CpuCoreTrackModel
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuCoreTrackRendererTest {
  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCoreTrackTestChannel", transportService)

  private lateinit var profilers: StudioProfilers

  @Before
  @Throws(Exception::class)
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
  }

  @Test
  fun render() {
    val coreTrackModel = TrackModel.newBuilder(
      CpuCoreTrackModel(
        LazyDataSeries { emptyList<SeriesData<CpuThreadSliceInfo>>() }, Range(), 123),
      ProfilerTrackRendererType.CPU_CORE,
      "Foo")
      .build()
    assertThat(coreTrackModel.dataModel.appProcessId).isEqualTo(123)
    val component = CpuCoreTrackRenderer().render(coreTrackModel)
    assertThat(component).isInstanceOf(StateChart::class.java)
  }

  @Test
  fun colorByThreadName() {
    val colorProvider = CpuCoreTrackRenderer.CpuCoreColorProvider()
    val thread1 = CpuThreadSliceInfo(1, "fooThread", 1000, "fooProcess")
    val thread2 = CpuThreadSliceInfo(2, "barThread", 1000, "fooProcess")
    assertThat(colorProvider.getColor(false, thread1)).isNotEqualTo(colorProvider.getColor(false, thread2))
  }
}