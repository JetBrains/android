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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JComboBox

class CpuProfilingConfigurationViewTest {

  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", cpuService,
                                    FakeTransportService(timer), FakeProfilerService(timer),
                                    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())
  private lateinit var stage: CpuProfilerStage
  private lateinit var configurationView: CpuProfilingConfigurationView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.name), FakeIdeProfilerServices(), timer)
    // One second must be enough for new devices (and processes) to be picked up
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
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
  fun separatorShouldNotBeSelectable() {
    val combobox = configurationView.component as JComboBox<*>
    assertThat(combobox.getItemAt(0)).isNotEqualTo(CpuProfilingConfigurationView.CONFIG_SEPARATOR_ENTRY)
    assertThat(combobox.getItemAt(1)).isEqualTo(CpuProfilingConfigurationView.CONFIG_SEPARATOR_ENTRY)

    combobox.selectedIndex = 0
    assertThat(combobox.selectedIndex).isEqualTo(0)

    combobox.selectedIndex = 1
    assertThat(combobox.selectedIndex).isEqualTo(0)
  }

  @Test
  fun separatorAndEditEntriesAreNotEqual() {
    assertThat(CpuProfilingConfigurationView.CONFIG_SEPARATOR_ENTRY).isNotEqualTo(CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY)
  }

  @Test
  fun apiInitiatedCaptureShouldShowSpecialConfig() {
    assertThat(cpuService.traceType).isEqualTo(Cpu.CpuTraceType.ART)
    val config = ProfilingConfiguration("My Config", Cpu.CpuTraceType.SIMPLEPERF, Cpu.CpuTraceMode.SAMPLED)
    stage.profilerConfigModel.profilingConfiguration = config

    // Verify non-API-initiated config before the API tracing starts.
    assertThat(configurationView.profilingConfiguration).isEqualTo(config)
    assertThat(configurationView.profilingConfigurations.size).isGreaterThan(1)

    // API-initiated tracing starts.
    val apiTracingconfig = CpuProfiler.CpuProfilerConfiguration.newBuilder().setTraceType(Cpu.CpuTraceType.ART).build()
    val startTimestamp: Long = 100
    cpuService.setOngoingCaptureConfiguration(apiTracingconfig, startTimestamp, Cpu.TraceInitiationType.INITIATED_BY_API)
    stage.updateProfilingState(true)

    // Verify the configuration is set to the special config properly.
    assertThat(configurationView.profilingConfiguration.name).isEqualTo("Debug API (Java)")
    assertThat(configurationView.profilingConfiguration.traceType).isEqualTo(Cpu.CpuTraceType.ART)
    assertThat(configurationView.profilingConfigurations.size).isEqualTo(1)
    assertThat(stage.captureState).isEqualTo(CpuProfilerStage.CaptureState.CAPTURING)

    // API-initiated tracing ends.
    cpuService.setAppBeingProfiled(false)
    stage.updateProfilingState(true)

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
    assertThat(configurationView.profilingConfiguration.name).isEqualTo(FakeIdeProfilerServices.FAKE_ART_SAMPLED_NAME)

    // Set a new configuration and check it's actually set as stage's profiling configuration
    val instrumented = ProfilingConfiguration(FakeIdeProfilerServices.FAKE_ART_INSTRUMENTED_NAME,
                                              Cpu.CpuTraceType.ART,
                                              Cpu.CpuTraceMode.INSTRUMENTED)
    configurationView.profilingConfiguration = instrumented
    assertThat(configurationView.profilingConfiguration.name).isEqualTo(FakeIdeProfilerServices.FAKE_ART_INSTRUMENTED_NAME)

    // Set CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY as profiling configuration
    // and check it doesn't actually replace the current configuration
    configurationView.profilingConfiguration = CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY
    assertThat(configurationView.profilingConfiguration.name).isEqualTo(FakeIdeProfilerServices.FAKE_ART_INSTRUMENTED_NAME)

    // Just sanity check "Instrumented" is not the name of CpuProfilerStage.EDIT_CONFIGURATIONS_ENTRY
    assertThat(
      CpuProfilingConfigurationView.EDIT_CONFIGURATIONS_ENTRY.name).isNotEqualTo(FakeIdeProfilerServices.FAKE_ART_INSTRUMENTED_NAME)
  }

  @Test
  fun nullProcessShouldNotThrowException() {
    // Set a device to null (e.g. when stop profiling) should not crash the CpuProfilerStage
    stage.studioProfilers.setProcess(null, null)
    assertThat(stage.studioProfilers.device as kotlin.Any?).isNull()

    // Open the profiling configurations dialog with null device shouldn't crash CpuProfilerStage.
    // Dialog is expected to be open so the user can edit configurations to be used by other devices later.
    configurationView.openProfilingConfigurationsDialog()
  }
}
