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
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profilers.*
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuProfilingConfigurationViewTest {

  private val cpuService = FakeCpuService()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", cpuService, FakeProfilerService(),
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

  @Test
  fun apiInitiatedCaptureShouldShowSpecialConfig() {
    assertThat(cpuService.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.ART)
    val config = ProfilingConfiguration("My Config", CpuProfiler.CpuProfilerType.SIMPLEPERF,
                                        CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED)
    stage.profilerConfigModel.profilingConfiguration = config

    // Verify non-API-initiated config before the API tracing starts.
    assertThat(configurationView.profilingConfiguration).isEqualTo(config)
    assertThat(configurationView.profilingConfigurations.size).isGreaterThan(1)

    // API-initiated tracing starts.
    val apiTracingconfig = CpuProfiler.CpuProfilerConfiguration.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.ART).build()
    val startTimestamp: Long = 100
    cpuService.setOngoingCaptureConfiguration(apiTracingconfig, startTimestamp, CpuProfiler.TraceInitiationType.INITIATED_BY_API)
    stage.updateProfilingState()

    // Verify the configuration is set to the special config properly.
    assertThat(configurationView.profilingConfiguration.name).isEqualTo("Debug API (Java)")
    assertThat(configurationView.profilingConfiguration.profilerType).isEqualTo(CpuProfiler.CpuProfilerType.ART)
    assertThat(configurationView.profilingConfigurations.size).isEqualTo(1)
    assertThat(stage.captureState).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING)

    // API-initiated tracing ends.
    cpuService.setAppBeingProfiled(false)
    stage.updateProfilingState()

    // Verify the configuration is set back to the config before API-initiated tracing.
    assertThat(configurationView.profilingConfiguration).isEqualTo(config)
    assertThat(configurationView.profilingConfigurations.size).isGreaterThan(1)
    assertThat(stage.captureState).isEqualTo(CpuProfilerStage.CaptureState.IDLE)
  }

  @Test
  fun editConfigurationsEntryCantBeSetAsProfilingConfiguration() {
    assertThat(configurationView.profilingConfiguration).isNotNull()
    // ART Sampled should be the default configuration when starting the stage,
    // as it's the first configuration on the list.
    assertThat(configurationView.profilingConfiguration.name).isEqualTo(ProfilingConfiguration.ART_SAMPLED)

    // Set a new configuration and check it's actually set as stage's profiling configuration
    val instrumented = ProfilingConfiguration(ProfilingConfiguration.ART_INSTRUMENTED,
                                              CpuProfiler.CpuProfilerType.ART,
                                              CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED)
    configurationView.profilingConfiguration = instrumented
    assertThat(configurationView.profilingConfiguration.name).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)

    // Set CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY as profiling configuration
    // and check it doesn't actually replace the current configuration
    configurationView.profilingConfiguration = CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY
    assertThat(configurationView.profilingConfiguration.name).isEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)

    // Just sanity check "Instrumented" is not the name of CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY
    assertThat(CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY.name).isNotEqualTo(ProfilingConfiguration.ART_INSTRUMENTED)
  }

  @Test
  fun nullProcessShouldNotThrowException() {
    // Set a device to null (e.g. when stop profiling) should not crash the CpuProfilerStage
    stage.studioProfilers.device = null
    assertThat(stage.studioProfilers.device as kotlin.Any?).isNull()

    // Open the profiling configurations dialog with null device shouldn't crash CpuProfilerStage.
    // Dialog is expected to be open so the user can edit configurations to be used by other devices later.
    configurationView.openProfilingConfigurationsDialog()
  }
}
