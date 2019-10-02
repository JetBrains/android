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
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class CustomEventProfilerStageTest {
  private val timer = FakeTimer()
  private val profilerClient = ProfilerClient(CustomEventProfilerStageTest::class.java.simpleName)
  private val services = FakeIdeProfilerServices()

  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    profilers = StudioProfilers(profilerClient, services, timer)
  }

  @Test
  fun trackGroupModelsAreSet() {
    val stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage
    stage.enter()
    assertThat(stage.trackGroupModels.size).isEqualTo(1)
    val interactionTrackGroup = stage.trackGroupModels[0]
    assertThat(interactionTrackGroup.title).isEqualTo("Custom Events")
    assertThat(interactionTrackGroup.size).isEqualTo(0)

  }

  @Test
  fun testInitTrackModels() {
    val stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage
    stage.eventNames.add("test1");
    stage.eventNames.add("test2")
    stage.enter()

    assertThat(stage.trackGroupModels.size).isEqualTo(1)
    val trackGroup1 = stage.trackGroupModels[0]

    assertThat(trackGroup1.size()).isEqualTo(2)

    // Test expected track model titles.
    assertThat(trackGroup1[0].title).isEqualTo("test1")
    assertThat(trackGroup1[1].title).isEqualTo("test2")

    // Test expected track model's data model types in the track group.
    assertThat(trackGroup1[0].dataModel).isInstanceOf(CustomEventTrackModel::class.java)
    assertThat(trackGroup1[1].dataModel).isInstanceOf(CustomEventTrackModel::class.java)

    // Test expected data model's line chart model and axis model.
    val track1 = trackGroup1[0].dataModel as CustomEventTrackModel
    assertThat(track1.axisComponentModel).isInstanceOf(ResizingAxisComponentModel::class.java)
    val lineChart1 = track1.lineChartModel as UserCounterModel
    assertThat(lineChart1.eventName).isEqualTo("test1")

    val track2 = trackGroup1[1].dataModel as CustomEventTrackModel
    assertThat(track2.axisComponentModel).isInstanceOf(ResizingAxisComponentModel::class.java)
    val lineChart2 = track2.lineChartModel as UserCounterModel
    assertThat(lineChart2.eventName).isEqualTo("test2")
  }
}