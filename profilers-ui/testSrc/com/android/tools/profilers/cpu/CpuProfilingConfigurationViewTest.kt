/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profilers.*
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuProfilingConfigurationViewTest {

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", FakeCpuService(), FakeProfilerService(),
                                      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())
  private val timer = FakeTimer()
  private lateinit var stage: CpuProfilerStage
  private lateinit var configurationView: CpuProfilingConfigurationView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(grpcChannel.getClient(),  FakeIdeProfilerServices(), timer)
    // One second must be enough for new devices (and processes) to be picked up
    profilers.setPreferredProcess(FakeProfilerService.FAKE_DEVICE_NAME, FakeProfilerService.FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    configurationView = CpuProfilingConfigurationView(stage, FakeIdeProfilerComponents())
  }

  @Test
  fun editConfigurationEntryExists() {
    assertThat(configurationView.profilingConfigurations).contains(CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY)
    assertThat(CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY.name).isNotEmpty()
  }

  @Test
  fun separatorEntryExists() {
    assertThat(configurationView.profilingConfigurations).contains(CpuProfilingConfigurationView.CONFIG_SEPARATOR_ENTRY)
    assertThat(CpuProfilingConfigurationView.CONFIG_SEPARATOR_ENTRY.name).isNotEmpty()
  }

  @Test
  fun separatorAndEditEntriesAreNotEqual() {
    assertThat(CpuProfilingConfigurationView.CONFIG_SEPARATOR_ENTRY).isNotEqualTo(CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY)
  }
}
