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
package com.android.tools.profilers.customevent

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.LineChartModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CustomEventTrackRendererTest {
  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CustomEventTrackTestChannel", FakeCpuService(), FakeProfilerService(timer), transportService)
  private val profilerClient = ProfilerClient(grpcChannel.name)

  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    profilers = StudioProfilers(profilerClient, services, timer)
  }

  @Test
  fun testRendererComponents() {
    // Tests that the line chart and axis component are rendered in the track.
    val range = Range(0.0, 0.0)
    val rangedContinuousSeries = RangedContinuousSeries("", range, range, FakeDataSeries())
    val lineChartModel = LineChartModel()
    lineChartModel.add(rangedContinuousSeries)
    val fakeAxisComponent = ResizingAxisComponentModel.Builder(range, SingleUnitAxisFormatter(0, 0, 0, "")).build()
    val customEventTrackModel = TrackModel(CustomEventTrackModel(lineChartModel, fakeAxisComponent),
                                           ProfilerTrackRendererType.CUSTOM_EVENTS,
                                           "Custom Events")

    val renderer = CustomEventTrackRenderer()
    val component = renderer.render(customEventTrackModel)
    val treeWalker = TreeWalker(component)

    val trackLineChartModel = treeWalker.descendants().filterIsInstance(LineChart::class.java)
    Truth.assertThat(trackLineChartModel.size).isEqualTo(1)

    val trackAxisComponent = treeWalker.descendants().filterIsInstance(AxisComponent::class.java)
    Truth.assertThat(trackAxisComponent.size).isEqualTo(1)
  }

  private class FakeDataSeries
    : DataSeries<Long> {

    val func = { i: Int -> SeriesData(0L, i.toLong()) }

    override fun getDataForRange(range: Range?): MutableList<SeriesData<Long>> {
      return MutableList(1, func)
    }
  }
}