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
import com.android.tools.adtui.model.SeriesData
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

/**
 * Tests the UserCounterDataSeries that holds the event count for Custom Event Visualization.
 */
class UserCounterDataSeriesTest {

  private val groupId1 = 1L
  private val groupId2 = 2L

  private val USER_EVENTS = ImmutableList.of<Common.Event>(
    Common.Event.newBuilder()
      .setGroupId(groupId1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(100))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId2)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(1600))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId2)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(720))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(groupId2)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(722))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build()
  )

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  private lateinit var profilers: StudioProfilers
  private lateinit var userCounterDataSeries: UserCounterDataSeries

  @get:Rule
  var grpcChannel = FakeGrpcChannel("UserCounterDataSeriesTest", transportService)

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply {
      enableCustomEventVisualization(true)
    }
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    USER_EVENTS.forEach { event -> transportService.addEventToStream(1, event) }
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    userCounterDataSeries = UserCounterDataSeries(profilers.client.transportClient, profilers)
  }

  @Test
  fun testEmptyRange() {
    val series = UserCounterDataSeries(profilers.client.transportClient, profilers)
    val dataSeries = series.getDataForRange(Range())
    assertThat(dataSeries).isEmpty()
  }


  @Test
  fun testNonEmptyRange() {
    val dataSeriesForRange1 = userCounterDataSeries.getDataForRange(Range(0.0, 100000.0))
    assertThat(dataSeriesForRange1).containsExactly(SeriesData(0L, 1L))

    val dataSeriesForRange2 = userCounterDataSeries.getDataForRange(Range(700000.0, 1100000.0))
    assertThat(dataSeriesForRange2).containsExactly(SeriesData(700000L, 2L), SeriesData(1000000L, 0L))
  }


  @Test
  fun testEventsOutsideRange() {
    // Edge case: Check if events are counted that do not fall within range, but are still within the bucket that overlaps with the range
    val dataSeriesForRange = userCounterDataSeries.getDataForRange(Range(721000.0, 721500.0))

    // An event occurs at 700ms and 720ms, both outside the range but within the overlapping bucket
    assertThat(dataSeriesForRange).containsExactly(SeriesData(721000L, 2L))
  }

  @Test
  fun testBucketDistribution() {
    val dataSeriesForRange = userCounterDataSeries.getDataForRange(Range(0.0, 2800000.0))

    assertThat(dataSeriesForRange).containsExactly(SeriesData(0L, 1L), SeriesData(500000L, 2L), SeriesData(1000000L, 0L),
                                                   SeriesData(1500000L, 1L), SeriesData(2000000L, 0L))
  }
}


