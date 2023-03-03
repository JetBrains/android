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
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CustomEventProfiler
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserCounterModelTest {

  val groupId1 = "group1".hashCode().toLong()
  val groupId2 = "group2".hashCode().toLong()

  private val fakeData = ImmutableList.of<Common.Event>(
    Common.Event.newBuilder()
      .setGroupId(groupId1)
      .setTimestamp(2000)
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder()
          .setName(groupId1.toString())
          .setRecordedValue(1).build())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId1)
      .setTimestamp(3000)
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder()
          .setName(groupId1.toString())
          .setRecordedValue(2).build())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId2)
      .setTimestamp(1000)
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder()
          .setName(groupId2.toString())
          .setRecordedValue(50).build())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId2)
      .setTimestamp(4000)
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder()
          .setName(groupId2.toString())
          .setRecordedValue(0).build())
      .setIsEnded(true)
      .build()
  )

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("UserCounterModelTest", transportService)

  private lateinit var myUserCounterModelGroup1: UserCounterModel
  private lateinit var myUserCounterModelGroup2: UserCounterModel

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply {
      enableCustomEventVisualization(true)
    }
    fakeData.forEach { event -> transportService.addEventToStream(1, event) }

    myUserCounterModelGroup1 = UserCounterModel(
      StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer), "group1")
    myUserCounterModelGroup2 = UserCounterModel(
      StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer), "group2")

  }

  /**
   * Tests that the UserCounterModel class correctly creates a ranged series with only data from its given event name.
   */
  @Test
  fun testUserCounterSeries() {
    val series1 = myUserCounterModelGroup1.eventSeries.getSeriesForRange(Range(0.0, 4.0))
    Truth.assertThat(series1.size).isEqualTo(2)
    Truth.assertThat(series1[0].value).isEqualTo(1)
    Truth.assertThat(series1[1].value).isEqualTo(2)

    val series2 = myUserCounterModelGroup1.eventSeries.getSeriesForRange(Range(3.0, 4.0))
    Truth.assertThat(series2.size).isEqualTo(1)
    Truth.assertThat(series2[0].value).isEqualTo(2)

    val series3 = myUserCounterModelGroup2.eventSeries.getSeriesForRange(Range(0.0, 4.0))
    Truth.assertThat(series3.size).isEqualTo(2)
    Truth.assertThat(series3[0].value).isEqualTo(50)
    Truth.assertThat(series3[1].value).isEqualTo(0)

    val series4 = myUserCounterModelGroup2.eventSeries.getSeriesForRange(Range(2.0, 4.0))
    Truth.assertThat(series4.size).isEqualTo(1)
    Truth.assertThat(series4[0].value).isEqualTo(0)
  }

  /**
   * Tests that the UserCounterModel has the correct default range.
   */
  @Test
  fun testRange() {
    Truth.assertThat(myUserCounterModelGroup1.usageRange.min).isEqualTo(0.0)
    Truth.assertThat(myUserCounterModelGroup1.usageRange.max).isEqualTo(10.0)

    Truth.assertThat(myUserCounterModelGroup2.usageRange.min).isEqualTo(0.0)
    Truth.assertThat(myUserCounterModelGroup2.usageRange.max).isEqualTo(10.0)
  }
}