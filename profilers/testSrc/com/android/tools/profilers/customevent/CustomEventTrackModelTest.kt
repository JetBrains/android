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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CustomEventProfiler
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class CustomEventTrackModelTest {

  val groupId1 = "group1".hashCode().toLong()

  private val USER_EVENTS = ImmutableList.of<Common.Event>(
    Common.Event.newBuilder()
      .setGroupId(groupId1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(500))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder()
          .setName(groupId1.toString())
          .setRecordedValue(1).build())
      .setIsEnded(false)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(1000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder()
          .setName(groupId1.toString())
          .setRecordedValue(5).build())
      .setIsEnded(false)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(2000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder()
          .setName(groupId1.toString())
          .setRecordedValue(10).build())
      .setIsEnded(false)
      .build()
  )

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CustomEventTrackModelTest", transportService)

  private lateinit var userCounterModel: UserCounterModel
  private lateinit var customEventTrackModel: CustomEventTrackModel

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply {
      enableCustomEventVisualization(true)
    }

    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    userCounterModel = UserCounterModel(profilers, "group1")
    customEventTrackModel = CustomEventTrackModel(userCounterModel, profilers.timeline.dataRange)
  }

  @Test
  fun testTrackLegend() {
    val legend = customEventTrackModel.legends

    assertThat(legend.trackLegend.value).isEqualTo("N/A")
    USER_EVENTS.forEach { event -> transportService.addEventToStream(1, event) }
    assertThat(legend.trackLegend.value).isEqualTo("1")
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(legend.trackLegend.value).isEqualTo("5")
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(legend.trackLegend.value).isEqualTo("10")
  }

}