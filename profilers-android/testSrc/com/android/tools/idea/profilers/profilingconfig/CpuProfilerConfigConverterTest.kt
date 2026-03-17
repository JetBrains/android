/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.idea.profilers.profilingconfig.CpuProfilerConfigConverter.fromTaskTypeToConfigName
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.idea.run.profiler.CpuProfilerConfig.INSTRUMENTED_JAVA_CONFIG_NAME
import com.android.tools.idea.run.profiler.CpuProfilerConfig.NATIVE_ALLOCATIONS_CONFIG_NAME
import com.android.tools.idea.run.profiler.CpuProfilerConfig.SAMPLED_JAVA_CONFIG_NAME
import com.android.tools.idea.run.profiler.CpuProfilerConfig.SAMPLED_NATIVE_CONFIG_NAME
import com.android.tools.idea.run.profiler.CpuProfilerConfig.SYSTEM_TRACE_CONFIG_NAME
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfigurationLegacy
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfigurationWallClock
import com.android.tools.profilers.cpu.config.ArtSampledConfigurationLegacy
import com.android.tools.profilers.cpu.config.ArtSampledConfigurationWallClock
import com.android.tools.profilers.cpu.config.AtraceConfiguration
import com.android.tools.profilers.cpu.config.ImportedConfiguration
import com.android.tools.profilers.cpu.config.LeakCanaryConfiguration
import com.android.tools.profilers.cpu.config.LeakCanaryMode
import com.android.tools.profilers.cpu.config.PerfettoNativeAllocationsConfiguration
import com.android.tools.profilers.cpu.config.PerfettoSystemTraceConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Test

class CpuProfilerConfigConverterTest {
  companion object {
    @JvmStatic
    @AfterClass
    fun tearDown() {
      StudioFlags.PROFILER_TRACEBOX.clearOverride()
      StudioFlags.PROFILER_METHOD_TRACE_IN_EDITOR.clearOverride()
    }
  }

  @Test
  fun toProfilingConfigurationSampledLegacy() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SAMPLED_JAVA
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
        dualClock = true
      }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(ArtSampledConfigurationLegacy::class.java)
    assertThat((profilingConfiguration as ArtSampledConfigurationLegacy).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(config.samplingIntervalUs)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
    assertThat(profilingConfiguration.dualClock).isEqualTo(config.dualClock)
  }

  @Test
  fun toProfilingConfigurationSampledWallClock() {
    StudioFlags.PROFILER_METHOD_TRACE_IN_EDITOR.override(true)
    try {
      val config =
        CpuProfilerConfig().apply {
          name = "MyConfiguration"
          technology = CpuProfilerConfig.Technology.SAMPLED_JAVA
          samplingIntervalUs = 1234
          bufferSizeMb = 5678
          dualClock = true
        }

      val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
      assertThat(profilingConfiguration).isInstanceOf(ArtSampledConfigurationWallClock::class.java)
      assertThat((profilingConfiguration as ArtSampledConfigurationWallClock).name).isEqualTo(config.name)
      assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
      assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(config.samplingIntervalUs)
      assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
      assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
      assertThat(profilingConfiguration.dualClock).isFalse()
    } finally {
      StudioFlags.PROFILER_METHOD_TRACE_IN_EDITOR.clearOverride()
    }
  }

  @Test
  fun toProfilingConfigurationInstrumentedLegacy() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.INSTRUMENTED_JAVA
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
        dualClock = false
      }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.UPSIDE_DOWN_CAKE)
    assertThat(profilingConfiguration).isInstanceOf(ArtInstrumentedConfigurationLegacy::class.java)
    assertThat((profilingConfiguration as ArtInstrumentedConfigurationLegacy).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
    assertThat(profilingConfiguration.dualClock).isEqualTo(config.dualClock)
  }

  @Test
  fun toProfilingConfigurationInstrumentedWallClock() {
    StudioFlags.PROFILER_METHOD_TRACE_IN_EDITOR.override(true)
    try {
      val config =
        CpuProfilerConfig().apply {
          name = "MyConfiguration"
          technology = CpuProfilerConfig.Technology.INSTRUMENTED_JAVA
          samplingIntervalUs = 1234
          bufferSizeMb = 5678
          dualClock = true
        }

      val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.UPSIDE_DOWN_CAKE)
      assertThat(profilingConfiguration).isInstanceOf(ArtInstrumentedConfigurationWallClock::class.java)
      assertThat((profilingConfiguration as ArtInstrumentedConfigurationWallClock).name).isEqualTo(config.name)
      assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
      assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
      assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
      assertThat(profilingConfiguration.dualClock).isFalse()
    } finally {
      StudioFlags.PROFILER_METHOD_TRACE_IN_EDITOR.clearOverride()
    }
  }

  @Test
  fun toProfilingConfigurationSampledNative() {
    val config =
      CpuProfilerConfig().apply {
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
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceM() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(true)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.M)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoSystemTraceConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoSystemTraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.M)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxDisabledForDeviceP() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(false)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoSystemTraceConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoSystemTraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.P)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceO() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(true)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.O)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoSystemTraceConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoSystemTraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.M)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxDisabledForDeviceO() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(false)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.O)
    assertThat(profilingConfiguration).isInstanceOf(AtraceConfiguration::class.java)
    assertThat((profilingConfiguration as AtraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ATRACE)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.N)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceLessThanM() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(true)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.LOLLIPOP)
    assertThat(profilingConfiguration).isInstanceOf(AtraceConfiguration::class.java)
    assertThat((profilingConfiguration as AtraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ATRACE)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.N)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxEnabledForDeviceQ() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(true)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.Q)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoSystemTraceConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoSystemTraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.M)
  }

  @Test
  fun toProfilingConfigurationSystemTraceWithTraceboxDisabledForDeviceQ() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(false)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.Q)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoSystemTraceConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoSystemTraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.P)
  }

  @Test
  fun toProfilingConfigurationSystemTracePreP() {
    val config =
      CpuProfilerConfig().apply {
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
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.SYSTEM_TRACE
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    StudioFlags.PROFILER_TRACEBOX.override(false)
    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoSystemTraceConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoSystemTraceConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(AndroidVersion.VersionCodes.P)
  }

  @Test
  fun toProfilingConfigurationUnspecified() {
    // Not going to specify name and technology to trigger CpuProfilerConfig's default constructor.
    // This should default to use sampled java configuration (ART Sampled).
    val config =
      CpuProfilerConfig().apply {
        samplingIntervalUs = 1234
        bufferSizeMb = 5678
      }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.P)
    assertThat(profilingConfiguration).isInstanceOf(ArtSampledConfigurationLegacy::class.java)
    assertThat((profilingConfiguration as ArtSampledConfigurationLegacy).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.ART)
    assertThat(profilingConfiguration.profilingSamplingIntervalUs).isEqualTo(1234)
    assertThat(profilingConfiguration.profilingBufferSizeInMb).isEqualTo(5678)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(0)
    assertThat(profilingConfiguration.dualClock).isEqualTo(false)
  }

  @Test
  fun toProfilingConfigurationPerfettoNativeAllocations() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.NATIVE_ALLOCATIONS
        samplingRateBytes = 1234
      }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(PerfettoNativeAllocationsConfiguration::class.java)
    assertThat((profilingConfiguration as PerfettoNativeAllocationsConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.PERFETTO)
    assertThat(profilingConfiguration.memorySamplingIntervalBytes).isEqualTo(1234)
    assertThat(profilingConfiguration.requiredDeviceLevel).isEqualTo(29)
  }

  @Test
  fun toCpuProfilerConfigPerfettoNativeAllocations() {
    val configuration = PerfettoNativeAllocationsConfiguration("MyConfiguration").apply { memorySamplingIntervalBytes = 1234 }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.NATIVE_ALLOCATIONS)
    assertThat(cpuProfilerConfig.samplingRateBytes).isEqualTo(1234)
  }

  @Test
  fun toCpuProfilerConfigArtSampledLegacy() {
    val configuration =
      ArtSampledConfigurationLegacy("MyConfiguration").apply {
        profilingSamplingIntervalUs = 1234
        profilingBufferSizeInMb = 5678
        dualClock = true
      }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(1234)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(5678)
    assertThat(cpuProfilerConfig.dualClock).isEqualTo(configuration.dualClock)
  }

  @Test
  fun toCpuProfilerConfigArtSampledWallClock() {
    val configuration =
      ArtSampledConfigurationWallClock("MyConfiguration").apply {
        profilingSamplingIntervalUs = 1234
        profilingBufferSizeInMb = 5678
        dualClock = true
      }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(1234)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(5678)
    assertThat(cpuProfilerConfig.dualClock).isFalse()
  }

  @Test
  fun toCpuProfilerConfigArtInstrumentedLegacy() {
    val configuration = ArtInstrumentedConfigurationLegacy("MyConfiguration").apply { profilingBufferSizeInMb = 1234 }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(1234)
    assertThat(cpuProfilerConfig.dualClock).isEqualTo(ProfilingConfiguration.DEFAULT_DUAL_CLOCK_VALUE)
  }

  @Test
  fun toCpuProfilerConfigArtInstrumentedWallClock() {
    val configuration = ArtInstrumentedConfigurationWallClock("MyConfiguration").apply { profilingBufferSizeInMb = 1234 }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(1234)
    assertThat(cpuProfilerConfig.dualClock).isFalse()
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
    val configuration = SimpleperfConfiguration("MyConfiguration").apply { profilingSamplingIntervalUs = 1234 }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_NATIVE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(1234)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
  }

  @Test
  fun toCpuProfilerConfigPerfettoSystemTrace() {
    val configuration = PerfettoSystemTraceConfiguration("MyConfiguration", false)

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SYSTEM_TRACE)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB)
  }

  @Test
  fun toProfilingConfigurationLeakCanary() {
    val config =
      CpuProfilerConfig().apply {
        name = "MyConfiguration"
        technology = CpuProfilerConfig.Technology.LEAKCANARY
        useStudioLeakCanaryMode = true
        leakCanaryThreshold = 10
      }

    val profilingConfiguration = CpuProfilerConfigConverter.toProfilingConfiguration(config, AndroidVersion.VersionCodes.N)
    assertThat(profilingConfiguration).isInstanceOf(LeakCanaryConfiguration::class.java)
    assertThat((profilingConfiguration as LeakCanaryConfiguration).name).isEqualTo(config.name)
    assertThat(profilingConfiguration.traceType).isEqualTo(TraceType.LEAKCANARY)
    assertThat(profilingConfiguration.source).isEqualTo(LeakCanaryMode.STUDIO)
    assertThat(profilingConfiguration.threshold).isEqualTo(10)
  }

  @Test
  fun toCpuProfilerConfigLeakCanary() {
    val configuration =
      LeakCanaryConfiguration("MyConfiguration").apply {
        source = LeakCanaryMode.STUDIO
        threshold = 10
      }

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.LEAKCANARY)
    assertThat(cpuProfilerConfig.useStudioLeakCanaryMode).isTrue()
    assertThat(cpuProfilerConfig.leakCanaryThreshold).isEqualTo(10)
  }

  @Test
  fun toCpuProfilerConfigUnspecified() {
    val configuration = UnspecifiedConfiguration("MyConfiguration")

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo(configuration.name)
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
    assertThat(cpuProfilerConfig.dualClock).isEqualTo(ProfilingConfiguration.DEFAULT_DUAL_CLOCK_VALUE)
  }

  @Test
  fun toCpuProfilerConfigImported() {
    val configuration = ImportedConfiguration()

    val cpuProfilerConfig = CpuProfilerConfigConverter.fromProfilingConfiguration(configuration)
    assertThat(cpuProfilerConfig.name).isEqualTo("Imported")
    assertThat(cpuProfilerConfig.technology).isEqualTo(CpuProfilerConfig.Technology.SAMPLED_JAVA)
    assertThat(cpuProfilerConfig.samplingIntervalUs).isEqualTo(ProfilingConfiguration.DEFAULT_SAMPLING_INTERVAL_US)
    assertThat(cpuProfilerConfig.bufferSizeMb).isEqualTo(ProfilingConfiguration.DEFAULT_BUFFER_SIZE_MB)
    assertThat(cpuProfilerConfig.dualClock).isEqualTo(ProfilingConfiguration.DEFAULT_DUAL_CLOCK_VALUE)
  }

  @Test
  fun testFromTaskTypeToConfigNameJavaKotlinMethodSample() {
    val result = fromTaskTypeToConfigName(ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING, TaskHomeTabModel.TaskRecordingType.SAMPLED)
    assertThat(SAMPLED_JAVA_CONFIG_NAME).isEqualTo(result)
  }

  @Test
  fun testFromTaskTypeToConfigNameJavaKotlinMethodTrace() {
    val result = fromTaskTypeToConfigName(ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING, TaskHomeTabModel.TaskRecordingType.INSTRUMENTED)
    assertThat(INSTRUMENTED_JAVA_CONFIG_NAME).isEqualTo(result)
  }

  @Test
  fun testFromTaskTypeToConfigNameCallstackSample() {
    val result = fromTaskTypeToConfigName(ProfilerTaskType.CALLSTACK_SAMPLE, null)
    assertThat(SAMPLED_NATIVE_CONFIG_NAME).isEqualTo(result)
  }

  @Test
  fun testFromTaskTypeToConfigNameSystemTrace() {
    val result = fromTaskTypeToConfigName(ProfilerTaskType.SYSTEM_TRACE, null)
    assertThat(SYSTEM_TRACE_CONFIG_NAME).isEqualTo(result)
  }

  @Test
  fun testFromTaskTypeToConfigNameNativeAllocations() {
    val result = fromTaskTypeToConfigName(ProfilerTaskType.NATIVE_ALLOCATIONS, null)
    assertThat(NATIVE_ALLOCATIONS_CONFIG_NAME).isEqualTo(result)
  }

  @Test
  fun testFromTaskTypeToConfigNameUnspecifiedTaskType() {
    // Test handling of unspecified task type
    val result = fromTaskTypeToConfigName(ProfilerTaskType.UNSPECIFIED, null)
    assertThat("").isEqualTo(result)
  }
}
