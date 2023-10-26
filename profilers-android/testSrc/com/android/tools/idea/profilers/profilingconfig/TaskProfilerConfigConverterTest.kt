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
package com.android.tools.idea.profilers.profilingconfig

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.profiler.TaskSettingConfig
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration
import com.android.tools.profilers.cpu.config.AtraceConfiguration
import com.android.tools.profilers.cpu.config.ImportedConfiguration
import com.android.tools.profilers.cpu.config.PerfettoConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Test

class TaskProfilerConfigConverterTest {
  companion object {
    @JvmStatic
    @AfterClass
    fun tearDown() {
      StudioFlags.PROFILER_TRACEBOX.clearOverride()
    }
  }

  @Test
  fun toProfilingConfigurationSampledJava() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SAMPLED_JAVA
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(ArtSampledConfiguration::class.java)
    assertThat((profilingConfiguration as ArtSampledConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(config.samplingIntervalUs)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun toProfilingConfigurationInstrumentedJava() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.INSTRUMENTED_JAVA
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(ArtInstrumentedConfiguration::class.java)
    assertThat((profilingConfiguration as ArtInstrumentedConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun toProfilingConfigurationSampledNative() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SAMPLED_NATIVE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(SimpleperfConfiguration::class.java)
    assertThat((profilingConfiguration as SimpleperfConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.SIMPLEPERF)
    assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(1234)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.O)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceM() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(true);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.M)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.M)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxDisabledForDeviceP() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(false);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.P)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceO() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(true);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.O)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.M)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxDisabledForDeviceO() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(false);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.O)
    assertThat(profilingConfiguration).isInstanceOf(AtraceConfiguration::class.java)
    assertThat((profilingConfiguration as AtraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ATRACE)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.N)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceLessThanM() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(true);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.LOLLIPOP)
    assertThat(profilingConfiguration).isInstanceOf(AtraceConfiguration::class.java)
    assertThat((profilingConfiguration as AtraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ATRACE)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.N)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceQ() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(true);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.Q)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.M)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxDisabledForDeviceQ() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(false);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.Q)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.P)
  }

  @Test
  fun toProfilingConfigurationSystemTracePreP() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.O)
    assertThat(profilingConfiguration).isInstanceOf(AtraceConfiguration::class.java)
    assertThat((profilingConfiguration as AtraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ATRACE)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.N)
  }

  @Test
  fun toProfilingConfigurationSystemTracePAndAbove() {
    val config = TaskSettingConfig().apply {
      name = "MyConfiguration"
      technology = TaskSettingConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    StudioFlags.PROFILER_TRACEBOX.override(false);
    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.P)
  }

  @Test
  fun toProfilingConfigurationUnspecified() {
    // Not going to specify name and technology to trigger CpuProfilerConfig's default constructor.
    // This should default to use sampled java configuration (ART Sampled).
    val config = TaskSettingConfig().apply {
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = TaskProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
    assertThat(profilingConfiguration).isInstanceOf(ArtSampledConfiguration::class.java)
    assertThat((profilingConfiguration as ArtSampledConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(1234)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun toCpuProfilerConfigArtSampled() {
    val configuration = ArtSampledConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
      profilingBufferSizeInMb = 5678
    }

    val cpuProfilerConfig = TaskProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(TaskSettingConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(1234)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(5678)
  }

  @Test
  fun toCpuProfilerConfigArtInstrumented() {
    val configuration = ArtInstrumentedConfiguration("MyConfiguration").apply {
      profilingBufferSizeInMb = 1234
    }

    val cpuProfilerConfig = TaskProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(TaskSettingConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(1234)
  }

  @Test
  fun toCpuProfilerConfigAtrace() {
    val configuration = AtraceConfiguration("MyConfiguration")

    val cpuProfilerConfig = TaskProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(TaskSettingConfig.Technology.SYSTEM_TRACE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigSimpleperf() {
    val configuration = SimpleperfConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
    }

    val cpuProfilerConfig = TaskProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(TaskSettingConfig.Technology.SAMPLED_NATIVE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(1234)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigPerfetto() {
    val configuration = PerfettoConfiguration("MyConfiguration", false)

    val cpuProfilerConfig = TaskProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(TaskSettingConfig.Technology.SYSTEM_TRACE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigUnspecified() {
    val configuration = UnspecifiedConfiguration("MyConfiguration")

    val cpuProfilerConfig = TaskProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(TaskSettingConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigImported() {
    val configuration = ImportedConfiguration()

    val cpuProfilerConfig = TaskProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo("Imported")
    assertThat(cpuProfilerConfig.technology).isEqualTo(TaskSettingConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
  }
}