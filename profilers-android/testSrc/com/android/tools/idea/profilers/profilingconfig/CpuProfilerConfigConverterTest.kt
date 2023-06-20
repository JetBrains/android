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
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration
import com.android.tools.profilers.cpu.config.AtraceConfiguration
import com.android.tools.profilers.cpu.config.ImportedConfiguration
import com.android.tools.profilers.cpu.config.PerfettoConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CpuProfilerConfigConverterTest {
  @Test
  fun toProfilingConfigurationSampledJava() {
    val config = CpuProfilerConfig().apply {
      name = "MyConfiguration"
      technology = CpuProfilerConfig.Technology.SAMPLED_JAVA
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(ArtSampledConfiguration::class.java)
    assertThat((profilingConfiguration as ArtSampledConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(config.samplingIntervalUs)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun toProfilingConfigurationInstrumentedJava() {
    val config = CpuProfilerConfig().apply {
      name = "MyConfiguration"
      technology = CpuProfilerConfig.Technology.INSTRUMENTED_JAVA
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(ArtInstrumentedConfiguration::class.java)
    assertThat((profilingConfiguration as ArtInstrumentedConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun toProfilingConfigurationSampledNative() {
    val config = CpuProfilerConfig().apply {
      name = "MyConfiguration"
      technology = CpuProfilerConfig.Technology.SAMPLED_NATIVE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(SimpleperfConfiguration::class.java)
    assertThat((profilingConfiguration as SimpleperfConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.SIMPLEPERF)
    assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(1234)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.O)
  }

  @Test
  fun toProfilingConfigurationSystemTracePreP() {
    val config = CpuProfilerConfig().apply {
      name = "MyConfiguration"
      technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.O)
    assertThat(profilingConfiguration).isInstanceOf(AtraceConfiguration::class.java)
    assertThat((profilingConfiguration as AtraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ATRACE)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.N)
  }

  @Test
  fun toProfilingConfigurationSystemTracePAndAbove() {
    val config = CpuProfilerConfig().apply {
      name = "MyConfiguration"
      technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.P)
  }

  @Test
  fun toProfilingConfigurationUnspecified() {
    // Not going to specify name and technology to trigger CpuProfilerConfig's default constructor.
    // This should default to use sampled java configuration (ART Sampled).
    val config = CpuProfilerConfig().apply {
      samplingIntervalUs = 1234
      bufferSizeMb = 5678
    }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
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

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(1234)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(5678)
  }

  @Test
  fun toCpuProfilerConfigArtInstrumented() {
    val configuration = ArtInstrumentedConfiguration("MyConfiguration").apply {
      profilingBufferSizeInMb = 1234
    }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(1234)
  }

  @Test
  fun toCpuProfilerConfigAtrace() {
    val configuration = AtraceConfiguration("MyConfiguration")

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SYSTEM_TRACE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigSimpleperf() {
    val configuration = SimpleperfConfiguration("MyConfiguration").apply {
      profilingSamplingIntervalUs = 1234
    }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_NATIVE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(1234)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigPerfetto() {
    val configuration = PerfettoConfiguration("MyConfiguration")

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SYSTEM_TRACE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigUnspecified() {
    val configuration = UnspecifiedConfiguration("MyConfiguration")

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigImported() {
    val configuration = ImportedConfiguration()

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo("Imported")
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
  }
}