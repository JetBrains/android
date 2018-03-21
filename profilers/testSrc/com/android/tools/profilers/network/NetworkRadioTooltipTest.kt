// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License")
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.network

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.NetworkProfiler.ConnectivityData
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val EPSILON = 0.00001

class NetworkRadioTooltipTest {
  private val fakeData = listOf(
      FakeNetworkService.newRadioData(1, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.HIGH),
      FakeNetworkService.newRadioData(2, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.LOW),
      FakeNetworkService.newRadioData(3, ConnectivityData.NetworkType.WIFI, ConnectivityData.RadioState.UNSPECIFIED)
  )

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
      "NetworkRadioTooltipTest", FakeProfilerService(true), FakeNetworkService.newBuilder().setNetworkDataList(fakeData).build())

  private lateinit var myTimer: FakeTimer
  private lateinit var myStage: NetworkProfilerStage
  private lateinit var myTooltip: NetworkRadioTooltip

  @Before
  fun setUp() {
    myTimer = FakeTimer()
    myStage = NetworkProfilerStage(StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), myTimer))
    myStage.studioProfilers.timeline.viewRange.set(TimeUnit.SECONDS.toMicros(0).toDouble(), TimeUnit.SECONDS.toMicros(5).toDouble())
    myStage.studioProfilers.timeline.dataRange.set(TimeUnit.SECONDS.toMicros(1).toDouble(), TimeUnit.SECONDS.toMicros(5).toDouble())
    myStage.studioProfilers.stage = myStage
    myStage.enter()

    myTooltip = NetworkRadioTooltip(myStage)
    myStage.tooltip = myTooltip
  }

  @Test
  fun noTooltipOutsideRange() {
    val tooltipTime = TimeUnit.MILLISECONDS.toMicros(500).toDouble()
    myStage.studioProfilers.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    assertThat(myTooltip.radioStateData).isNull()
  }

  @Test
  fun tooltipOnFirstRadioState() {
    val tooltipTime = TimeUnit.SECONDS.toMicros(1).toDouble()
    myStage.studioProfilers.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    val radioStateData = myTooltip.radioStateData!!
    assertThat(radioStateData.radioState).isEqualTo(NetworkRadioDataSeries.RadioState.HIGH)
    assertThat(radioStateData.radioStateRange.min).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(1).toDouble())
    assertThat(radioStateData.radioStateRange.max).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(2).toDouble())
  }

  @Test
  fun tooltipOnMiddleRadioState() {
    val tooltipTime = TimeUnit.SECONDS.toMicros(2).toDouble()
    myStage.studioProfilers.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    val radioStateData = myTooltip.radioStateData!!
    assertThat(radioStateData.radioState).isEqualTo(NetworkRadioDataSeries.RadioState.LOW)
    assertThat(radioStateData.radioStateRange.min).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(2).toDouble())
    assertThat(radioStateData.radioStateRange.max).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(3).toDouble())
  }

  @Test
  fun tooltipOnLastRadioState() {
    val tooltipTime = TimeUnit.SECONDS.toMicros(4).toDouble()
    myStage.studioProfilers.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    val radioStateData = myTooltip.radioStateData!!
    assertThat(radioStateData.radioState).isEqualTo(NetworkRadioDataSeries.RadioState.WIFI)
    assertThat(radioStateData.radioStateRange.min).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(3).toDouble())
    assertThat(radioStateData.radioStateRange.max).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(5).toDouble())
  }

  @Test
  fun radioStateRangeChangesWithDataRange() {
    val tooltipTime = TimeUnit.SECONDS.toMicros(4).toDouble()
    myStage.studioProfilers.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    myStage.studioProfilers.timeline.dataRange.max = TimeUnit.SECONDS.toMicros(6).toDouble()
    val radioStateData = myTooltip.radioStateData!!
    assertThat(radioStateData.radioState).isEqualTo(NetworkRadioDataSeries.RadioState.WIFI)
    assertThat(radioStateData.radioStateRange.min).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(3).toDouble())
    assertThat(radioStateData.radioStateRange.max).isWithin(EPSILON).of(TimeUnit.SECONDS.toMicros(6).toDouble())
  }
}
