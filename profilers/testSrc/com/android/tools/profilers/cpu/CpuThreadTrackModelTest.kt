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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuThreadTrackModelTest {
  private val timer = FakeTimer()
  private val services = FakeIdeProfilerServices()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuThreadTrackModelTest", transportService)
  private val profilerClient = ProfilerClient(grpcChannel.name)

  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    services.enableEventsPipeline(true)
    profilers = StudioProfilers(profilerClient, services, timer)
  }

  @Test
  fun noThreadStatesFromArtTrace() {
    val capture = CpuProfilerTestUtils.getValidCapture()
    val threadTrackModel = CpuThreadTrackModel(Range(), capture, CpuThreadInfo(516, "Foo"), DefaultTimeline(), MultiSelectionModel())
    assertThat(threadTrackModel.threadStateChartModel.series).isEmpty()
  }
}