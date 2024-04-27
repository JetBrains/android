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


class CustomEventMonitorTest {

  private val USER_EVENTS = ImmutableList.of<Common.Event>(
    // A light number of events at 1000ms.
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(1000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    // A medium number of events at 2000ms.
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(2000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(2001))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(2002))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(2003))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    // A heavy number of events at 3000ms.
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(3000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(3000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(3001))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(3001))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(3001))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(3002))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(3002))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder())
      .setIsEnded(true)
      .build()
  )
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer,true)

  private lateinit var profilers: StudioProfilers
  private lateinit var monitor: CustomEventMonitor

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CustomEventMonitorTest", transportService)
  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply {
      enableCustomEventVisualization(true)
    }
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    USER_EVENTS.forEach { event -> transportService.addEventToStream(1, event) }
    monitor = CustomEventMonitor(profilers)
  }

  @Test
  fun testStateChartModel() {
    val eventModel = monitor.eventModel

    // Test that exactly 1 series has been added to the state chart model in the monitor.
    assertThat(eventModel.series.size).isEqualTo(1)
  }

  @Test
  fun testLegend() {
    val legend = monitor.legend

    assertThat(legend.usageLegend.value).isEqualTo("None")
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(legend.usageLegend.value).isEqualTo("Light")
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(legend.usageLegend.value).isEqualTo("Medium")
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(legend.usageLegend.value).isEqualTo("Heavy")

  }
}