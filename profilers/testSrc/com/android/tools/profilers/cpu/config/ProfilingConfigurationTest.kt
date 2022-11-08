/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.config

import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Trace.TraceConfiguration
import com.android.tools.profiler.proto.Trace.TraceMode
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.AdditionalOptions
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import perfetto.protos.PerfettoConfig
import perfetto.protos.PerfettoConfig.ProcessStatsConfig
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType

class ProfilingConfigurationTest {

  @get:Rule
  val myThrown = ExpectedException.none()

  @Test
  fun fromProto() {
    val userOptions = Trace.UserOptions.newBuilder()
      .setName("MyConfiguration")
      .setTraceMode(Trace.TraceMode.SAMPLED)
      .setTraceType(Trace.UserOptions.TraceType.ART)
      .setSamplingIntervalUs(123)
      .setBufferSizeInMb(12)
      .build()
    val proto = Trace.TraceConfiguration.newBuilder()
      .setUserOptions(userOptions)
      .build()
    val config = ProfilingConfiguration.fromProto(proto)
    assertThat(config).isInstanceOf(ArtSampledConfiguration::class.java)
    val art = config as ArtSampledConfiguration
    assertThat(config.name).isEqualTo("MyConfiguration")
    assertThat(config).isInstanceOf(ArtSampledConfiguration::class.java)
    assertThat(config.traceType).isEqualTo(TraceType.ART)
    assertThat(config.profilingSamplingIntervalUs).isEqualTo(123)
    assertThat(config.profilingBufferSizeInMb).isEqualTo(12)
  }

  @Test
  fun toProto() {
    val configuration = SimpleperfConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
    }
    val proto = configuration.toProto()

    assertThat(proto.name).isEqualTo("MyConfiguration")
    assertThat(proto.traceMode).isEqualTo(Trace.TraceMode.SAMPLED)
    assertThat(proto.traceType).isEqualTo(Trace.UserOptions.TraceType.SIMPLEPERF)
    assertThat(proto.samplingIntervalUs).isEqualTo(1234)
  }

  @Test
  fun addOptionsArtSampledConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val artSampledConfiguration = ArtSampledConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
      profilingBufferSizeInMb = 5678
    }


    artSampledConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isTrue()
    assertThat(config.artOptions.traceMode).isEqualTo(TraceMode.SAMPLED)
    assertThat(config.artOptions.samplingIntervalUs).isEqualTo(1234)
    assertThat(config.artOptions.bufferSizeInMb).isEqualTo(5678)
  }

  @Test
  fun addOptionsArtInstrumentedConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val artInstrumentedConfiguration = ArtInstrumentedConfiguration("MyConfiguration").apply {
      profilingBufferSizeInMb = 1234
    }

    artInstrumentedConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isTrue()
    assertThat(config.artOptions.traceMode).isEqualTo(TraceMode.INSTRUMENTED)
    assertThat(config.artOptions.samplingIntervalUs).isEqualTo(0)
    assertThat(config.artOptions.bufferSizeInMb).isEqualTo(1234)
  }

  @Test
  fun addOptionsAtraceConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val atraceConfiguration = AtraceConfiguration("MyConfiguration").apply {
      profilingBufferSizeInMb = 1234
    }

    atraceConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasAtraceOptions()).isTrue()
    assertThat(config.atraceOptions.bufferSizeInMb).isEqualTo(1234)
  }

  @Test
  fun addOptionsSimpleperfConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val simpleperfConfiguration = SimpleperfConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
    }

    simpleperfConfiguration.addOptions(configBuilder, mapOf(AdditionalOptions.SYMBOL_DIRS to listOf("foo", "bar")))
    val config = configBuilder.build()

    assertThat(config.hasSimpleperfOptions()).isTrue()
    assertThat(config.simpleperfOptions.samplingIntervalUs).isEqualTo(1234)
    assertThat(config.simpleperfOptions.symbolDirsList).isEqualTo(listOf("foo", "bar"))
  }

  @Test
  fun addOptionsPerfettoConfigAddsSuccessfully() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val perfettoConfiguration = PerfettoConfiguration("MyConfiguration").apply {
      profilingBufferSizeInMb = 1234
    }

    perfettoConfiguration.addOptions(configBuilder, mapOf(AdditionalOptions.APP_PKG_NAME to "foo"))
    val config = configBuilder.build()

    assertThat(config.hasPerfettoOptions()).isTrue()
    assertThat(config.perfettoOptions.buffersCount).isEqualTo(2)
    assertThat(config.perfettoOptions.getBuffers(0).sizeKb).isEqualTo(1234 * 1024)
    // 256 Kb is the hardcoded value used for the secondary buffer
    assertThat(config.perfettoOptions.getBuffers(1).sizeKb).isEqualTo(256)
    assertThat(config.perfettoOptions.dataSourcesCount).isEqualTo(8)
    val actualDataSources = config.perfettoOptions.dataSourcesList

    // Verify first data source (Ftrace Data Source) is built correctly.
    assertThat(actualDataSources[0].config.name).isEqualTo("linux.ftrace")
    assertThat(actualDataSources[0].config.targetBuffer).isEqualTo(0)
    assertThat(actualDataSources[0].config.ftraceConfig.drainPeriodMs).isEqualTo(170)
    assertThat(actualDataSources[0].config.ftraceConfig.compactSched.enabled).isTrue()
    assertThat(actualDataSources[0].config.ftraceConfig.ftraceEventsList).containsExactly("thermal/thermal_temperature",
                                                                                          "perf_trace_counters/perf_trace_user",
                                                                                          "fence/signaled",
                                                                                          "fence/fence_wait_start",
                                                                                          "power/cpu_frequency",
                                                                                          "power/cpu_idle",
                                                                                          "task/task_rename",
                                                                                          "task/task_newtask"
    )
    assertThat(actualDataSources[0].config.ftraceConfig.atraceCategoriesList).containsExactly(
      "gfx", "input", "view", "wm", "am", "sm", "camera", "hal", "res", "pm", "ss", "power", "database",
      "binder_driver", "binder_lock", "sched", "freq"
    )
    assertThat(actualDataSources[0].config.perfEventConfig.allCpus).isTrue()
    assertThat(actualDataSources[0].config.perfEventConfig.targetCmdlineList).containsExactly("foo")
    assertThat(actualDataSources[0].config.ftraceConfig.atraceAppsCount).isEqualTo(1)

    // Verify second data source (First Process and Thread Names) is built correctly.
    assertThat(actualDataSources[1].config.name).isEqualTo("linux.process_stats")
    assertThat(actualDataSources[1].config.targetBuffer).isEqualTo(1)
    assertThat(actualDataSources[1].config.processStatsConfig.scanAllProcessesOnStart).isTrue()
    assertThat(actualDataSources[1].config.processStatsConfig.recordThreadNames).isTrue()

    // Verify second data source (Second Process and Thread Names) is built correctly.
    assertThat(actualDataSources[2].config.name).isEqualTo("linux.process_stats")
    assertThat(actualDataSources[2].config.targetBuffer).isEqualTo(0)
    assertThat(actualDataSources[2].config.processStatsConfig.procStatsPollMs).isEqualTo(1000)
    assertThat(actualDataSources[2].config.processStatsConfig.quirksList).containsExactly(ProcessStatsConfig.Quirks.DISABLE_ON_DEMAND)

    // Verify second data source (CPU Info) is built correctly.
    assertThat(actualDataSources[3].config.name).isEqualTo("linux.system_info")

    // Verify second data source (Lifecycle Data) is built correctly.
    assertThat(actualDataSources[4].config.name).isEqualTo("android.surfaceflinger.frame")

    // Verify second data source (Frame Timeline Data) is built correctly.
    assertThat(actualDataSources[5].config.name).isEqualTo("android.surfaceflinger.frametimeline")

    // Verify second data source (TrackEvent API Data) is built correctly.
    assertThat(actualDataSources[6].config.name).isEqualTo("track_event")

    // Verify second data source (Power Data) is built correctly.
    assertThat(actualDataSources[7].config.name).isEqualTo("android.power")
    assertThat(actualDataSources[7].config.targetBuffer).isEqualTo(0)
    assertThat(actualDataSources[7].config.androidPowerConfig.collectPowerRails).isTrue()
    assertThat(actualDataSources[7].config.androidPowerConfig.batteryCountersList).containsExactly(
      PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CAPACITY_PERCENT,
      PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CHARGE,
      PerfettoConfig.AndroidPowerConfig.BatteryCounters.BATTERY_COUNTER_CURRENT
    )
  }

  @Test
  fun addOptionsUnspecifiedConfigAddsNothing() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val unspecifiedConfiguration = UnspecifiedConfiguration("MyConfiguration");

    unspecifiedConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isFalse()
    assertThat(config.hasAtraceOptions()).isFalse()
    assertThat(config.hasSimpleperfOptions()).isFalse()
    assertThat(config.hasPerfettoOptions()).isFalse()
  }

  @Test
  fun addOptionsImportedConfigAddsNothing() {
    val configBuilder = TraceConfiguration.getDefaultInstance().toBuilder()
    val importedConfiguration = ImportedConfiguration();

    importedConfiguration.addOptions(configBuilder, emptyMap())
    val config = configBuilder.build()

    assertThat(config.hasArtOptions()).isFalse()
    assertThat(config.hasAtraceOptions()).isFalse()
    assertThat(config.hasSimpleperfOptions()).isFalse()
    assertThat(config.hasPerfettoOptions()).isFalse()
  }
}