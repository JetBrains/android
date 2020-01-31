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
    assertThat(stage.trackGroupListModel.size).isEqualTo(1)
    val interactionTrackGroup = stage.trackGroupListModel[0]
    assertThat(interactionTrackGroup.title).isEqualTo("Custom Events")
    assertThat(interactionTrackGroup.size).isEqualTo(0)

  }
}