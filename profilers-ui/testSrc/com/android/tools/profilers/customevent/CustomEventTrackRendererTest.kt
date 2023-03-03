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
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CustomEventTrackRendererTest {
  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CustomEventTrackTestChannel", transportService)

  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
  }

  @Test
  fun testRendererComponents() {
    // Tests that the line chart and axis component are rendered in the track.
    val lineChartModel = UserCounterModel(profilers, "foo")
    val customEventTrackModel = TrackModel.newBuilder(CustomEventTrackModel(lineChartModel, Range(0.0, 0.0)),
                                                      ProfilerTrackRendererType.CUSTOM_EVENTS,
                                                      "Custom Events").build()

    val renderer = CustomEventTrackRenderer()
    val component = renderer.render(customEventTrackModel)
    val treeWalker = TreeWalker(component)

    val trackLineChartModel = treeWalker.descendants().filterIsInstance(LineChart::class.java)
    Truth.assertThat(trackLineChartModel.size).isEqualTo(1)

    val trackAxisComponent = treeWalker.descendants().filterIsInstance(AxisComponent::class.java)
    Truth.assertThat(trackAxisComponent.size).isEqualTo(1)

    val trackLegendComponent = treeWalker.descendants().filterIsInstance(CustomEventTrackLegendComponent::class.java)
    Truth.assertThat(trackLegendComponent.size).isEqualTo(1)
  }
}