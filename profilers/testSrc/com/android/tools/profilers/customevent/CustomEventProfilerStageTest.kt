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
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CustomEventProfiler
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class CustomEventProfilerStageTest {

  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)

  private lateinit var profilers: StudioProfilers

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CustomEventProfilerStageTest", transportService)

  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
  }

  @Test
  fun trackGroupModelsAreSet() {
    val stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage
    stage.enter()
    assertThat(stage.trackGroupModels.size).isEqualTo(2)
    val interactionTrackGroup = stage.trackGroupModels[0]
    assertThat(interactionTrackGroup.title).isEqualTo("Interaction")
    assertThat(interactionTrackGroup.size).isEqualTo(2)
    val customEventsTrackGroup = stage.trackGroupModels[1]
    assertThat(customEventsTrackGroup.title).isEqualTo("Custom Events")
    assertThat(customEventsTrackGroup.size).isEqualTo(0)

  }

  @Test
  fun testInteractionTrackGroup() {
    val stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage
    stage.enter()

    assertThat(stage.trackGroupModels).isNotEmpty();
    assertThat(stage.trackGroupModels[0].size).isEqualTo(2)
    assertThat(stage.trackGroupModels[0][0].rendererType).isEqualTo(ProfilerTrackRendererType.USER_INTERACTION)
    assertThat(stage.trackGroupModels[0][1].rendererType).isEqualTo(ProfilerTrackRendererType.APP_LIFECYCLE)

  }

  @Test
  fun testInitTrackModels() {
    val stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage
    // Add first event to stream and check that it has been loaded into the track group model.
    transportService.addEventToStream(1, Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(100))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder().setName("test1"))
      .setIsEnded(true)
      .build())
    transportService.addEventToStream(1, Common.Event.newBuilder()
      .setGroupId(2)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(200))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder().setName("test2"))
      .setIsEnded(true)
      .build())
    stage.enter()

    stage.updateEventNames()
    assertThat(stage.trackGroupModels.size).isEqualTo(2)
    val trackGroupUserCounter = stage.trackGroupModels[1]

    assertThat(trackGroupUserCounter.size()).isEqualTo(2)

    // Test expected track model titles.
    assertThat(trackGroupUserCounter[0].title).isEqualTo("test1")
    assertThat(trackGroupUserCounter[1].title).isEqualTo("test2")

    // Test expected track model's data model types in the track group.
    assertThat(trackGroupUserCounter[0].dataModel).isInstanceOf(CustomEventTrackModel::class.java)
    assertThat(trackGroupUserCounter[1].dataModel).isInstanceOf(CustomEventTrackModel::class.java)

    // Test expected data model's line chart model and axis model.
    val track1 = trackGroupUserCounter[0].dataModel as CustomEventTrackModel
    assertThat(track1.axisComponentModel).isInstanceOf(ResizingAxisComponentModel::class.java)
    val lineChart1 = track1.lineChartModel as UserCounterModel
    assertThat(lineChart1.eventName).isEqualTo("test1")

    val track2 = trackGroupUserCounter[1].dataModel as CustomEventTrackModel
    assertThat(track2.axisComponentModel).isInstanceOf(ResizingAxisComponentModel::class.java)
    val lineChart2 = track2.lineChartModel as UserCounterModel
    assertThat(lineChart2.eventName).isEqualTo("test2")
  }

  @Test
  fun testDynamicTrackModelLoadingSameEvent() {

    val stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage
    stage.enter()

    assertThat(stage.trackGroupModels.size).isEqualTo(2)
    val trackGroupUserCounter = stage.trackGroupModels[1]
    assertThat(trackGroupUserCounter.size()).isEqualTo(0)

    // Add first event to stream and check that it has been loaded into the track group model.
    transportService.addEventToStream(1, Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(100))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder().setName("test1"))
      .setIsEnded(true)
      .build())

    stage.updateEventNames();
    assertThat(trackGroupUserCounter.size()).isEqualTo(1)
    val track1 = trackGroupUserCounter[0].dataModel as CustomEventTrackModel
    val lineChart1 = track1.lineChartModel as UserCounterModel
    assertThat(lineChart1.eventName).isEqualTo("test1")

    // Add event with same name to stream.
    transportService.addEventToStream(1, Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(1000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder().setName("test1"))
      .setIsEnded(true)
      .build())

    stage.updateEventNames()
    // Check that only one track is in the track group model since an event with the same name has been added.
    assertThat(trackGroupUserCounter.size()).isEqualTo(1)
  }

  @Test
  fun testDynamicTrackModelLoadingDifferentEvents() {

    val stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage
    stage.enter()

    assertThat(stage.trackGroupModels.size).isEqualTo(2)
    val trackGroupUserCounter = stage.trackGroupModels[1]
    assertThat(trackGroupUserCounter.size()).isEqualTo(0)

    // Add first event to stream and check that it has been loaded into the track group model.
    transportService.addEventToStream(1, Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(100))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder().setName("test1"))
      .setIsEnded(true)
      .build())

    stage.updateEventNames()
    assertThat(trackGroupUserCounter.size()).isEqualTo(1)
    val track1 = trackGroupUserCounter[0].dataModel as CustomEventTrackModel
    val lineChart1 = track1.lineChartModel as UserCounterModel
    assertThat(lineChart1.eventName).isEqualTo("test1")

    // Add a different event to stream.
    transportService.addEventToStream(1, Common.Event.newBuilder()
      .setGroupId(2)
      .setTimestamp(TimeUnit.MILLISECONDS.toNanos(1000))
      .setKind(Common.Event.Kind.USER_COUNTERS)
      .setUserCounters(
        CustomEventProfiler.UserCounterData.newBuilder().setName("test2"))
      .setIsEnded(true)
      .build())

    // Check that a second track has been added.
    stage.updateEventNames()
    assertThat(trackGroupUserCounter.size()).isEqualTo(2)
    val track2 = trackGroupUserCounter[1].dataModel as CustomEventTrackModel
    val lineChart2 = track2.lineChartModel as UserCounterModel
    assertThat(lineChart2.eventName).isEqualTo("test2")
  }
}