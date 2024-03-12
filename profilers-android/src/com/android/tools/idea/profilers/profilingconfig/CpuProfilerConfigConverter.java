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
package com.android.tools.idea.profilers.profilingconfig;

import static com.android.tools.idea.run.profiler.CpuProfilerConfig.INSTRUMENTED_JAVA_CONFIG_NAME;
import static com.android.tools.idea.run.profiler.CpuProfilerConfig.NATIVE_ALLOCATIONS_CONFIG_NAME;
import static com.android.tools.idea.run.profiler.CpuProfilerConfig.SAMPLED_JAVA_CONFIG_NAME;
import static com.android.tools.idea.run.profiler.CpuProfilerConfig.SAMPLED_NATIVE_CONFIG_NAME;
import static com.android.tools.idea.run.profiler.CpuProfilerConfig.SYSTEM_TRACE_CONFIG_NAME;
import static com.android.tools.profilers.cpu.config.ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB;

import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.AtraceConfiguration;
import com.android.tools.profilers.cpu.config.PerfettoNativeAllocationsConfiguration;
import com.android.tools.profilers.cpu.config.PerfettoSystemTraceConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration;
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration;
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel;
import com.android.tools.profilers.tasks.ProfilerTaskType;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;

public class CpuProfilerConfigConverter {

  private CpuProfilerConfigConverter() { }

  /**
   * Converts from a {@link ProfilingConfiguration} to a {@link CpuProfilerConfig}
   */
  public static CpuProfilerConfig fromProfilingConfiguration(ProfilingConfiguration config) {
    CpuProfilerConfig cpuProfilerConfig = null;

    switch (config.getTraceType()) {
      case ART:
        if (config instanceof ArtSampledConfiguration) {
          ArtSampledConfiguration artSampledConfiguration = (ArtSampledConfiguration)config;
          cpuProfilerConfig = new CpuProfilerConfig(artSampledConfiguration.getName(), CpuProfilerConfig.Technology.SAMPLED_JAVA);
          cpuProfilerConfig.setSamplingIntervalUs(artSampledConfiguration.getProfilingSamplingIntervalUs());
          cpuProfilerConfig.setBufferSizeMb(artSampledConfiguration.getProfilingBufferSizeInMb());
        }
        else {
          ArtInstrumentedConfiguration artInstrumentedConfiguration = (ArtInstrumentedConfiguration)config;
          cpuProfilerConfig = new CpuProfilerConfig(artInstrumentedConfiguration.getName(), CpuProfilerConfig.Technology.INSTRUMENTED_JAVA);
          cpuProfilerConfig.setBufferSizeMb(artInstrumentedConfiguration.getProfilingBufferSizeInMb());
        }
        break;
      case SIMPLEPERF:
        SimpleperfConfiguration simpleperfConfiguration = (SimpleperfConfiguration)config;
        cpuProfilerConfig = new CpuProfilerConfig(simpleperfConfiguration.getName(), CpuProfilerConfig.Technology.SAMPLED_NATIVE);
        cpuProfilerConfig.setSamplingIntervalUs(simpleperfConfiguration.getProfilingSamplingIntervalUs());
        break;
      case ATRACE:
        AtraceConfiguration atraceConfiguration = (AtraceConfiguration)config;
        cpuProfilerConfig = new CpuProfilerConfig(atraceConfiguration.getName(), CpuProfilerConfig.Technology.SYSTEM_TRACE);
        cpuProfilerConfig.setBufferSizeMb(SYSTEM_TRACE_BUFFER_SIZE_MB);
        break;
      case PERFETTO:
        if (config instanceof PerfettoNativeAllocationsConfiguration) {
          PerfettoNativeAllocationsConfiguration perfettoNativeAllocationsConfiguration = (PerfettoNativeAllocationsConfiguration)config;
          cpuProfilerConfig =
            new CpuProfilerConfig(perfettoNativeAllocationsConfiguration.getName(), CpuProfilerConfig.Technology.NATIVE_ALLOCATIONS);
          cpuProfilerConfig.setSamplingRateBytes(perfettoNativeAllocationsConfiguration.getMemorySamplingIntervalBytes());
        }
        else {
          PerfettoSystemTraceConfiguration perfettoSystemTraceConfiguration = (PerfettoSystemTraceConfiguration)config;
          cpuProfilerConfig = new CpuProfilerConfig(perfettoSystemTraceConfiguration.getName(), CpuProfilerConfig.Technology.SYSTEM_TRACE);
          cpuProfilerConfig.setBufferSizeMb(SYSTEM_TRACE_BUFFER_SIZE_MB);
        }
        break;
      case UNSPECIFIED:
        UnspecifiedConfiguration unspecifiedConfiguration = (UnspecifiedConfiguration)config;
        cpuProfilerConfig = new CpuProfilerConfig(unspecifiedConfiguration.getName(), CpuProfilerConfig.Technology.SAMPLED_JAVA);
        break;
    }

    return cpuProfilerConfig;
  }

  /**
   * Converts from a {@link CpuProfilerConfig} to a {@link ProfilingConfiguration}
   */
  public static ProfilingConfiguration toProfilingConfiguration(CpuProfilerConfig config, int deviceApi) {
    ProfilingConfiguration configuration = null;

    String name = config.getName();

    switch (config.getTechnology()) {
      case SAMPLED_JAVA:
        configuration = new ArtSampledConfiguration(name);
        ((ArtSampledConfiguration)configuration).setProfilingBufferSizeInMb(config.getBufferSizeMb());
        ((ArtSampledConfiguration)configuration).setProfilingSamplingIntervalUs(config.getSamplingIntervalUs());
        break;
      case INSTRUMENTED_JAVA:
        configuration = new ArtInstrumentedConfiguration(name);
        ((ArtInstrumentedConfiguration)configuration).setProfilingBufferSizeInMb(config.getBufferSizeMb());
        break;
      case SAMPLED_NATIVE:
        configuration = new SimpleperfConfiguration(name);
        ((SimpleperfConfiguration)configuration).setProfilingSamplingIntervalUs(config.getSamplingIntervalUs());
        break;
      case SYSTEM_TRACE:
        if (StudioFlags.PROFILER_TRACEBOX.get()) {
          if (deviceApi >= AndroidVersion.VersionCodes.M) {
            configuration = new PerfettoSystemTraceConfiguration(name, true);
            break;
          }
        }
        if (deviceApi >= AndroidVersion.VersionCodes.P) {
          configuration = new PerfettoSystemTraceConfiguration(name, false);
        }
        else {
          configuration = new AtraceConfiguration(name);
        }
        break;
      case NATIVE_ALLOCATIONS:
        configuration = new PerfettoNativeAllocationsConfiguration(name);
        ((PerfettoNativeAllocationsConfiguration)configuration).setMemorySamplingIntervalBytes(config.getSamplingRateBytes());
    }

    return configuration;
  }

  /**
   * Converts from list of {@link CpuProfilerConfig} to a list of {@link ProfilingConfiguration}
   */
  public static List<ProfilingConfiguration> toProfilingConfiguration(List<CpuProfilerConfig> configs, int deviceApi) {
    return ContainerUtil.map(configs, config -> toProfilingConfiguration(config, deviceApi));
  }

  /**
   * Converts from a {@link ProfilerTaskType} to the respective {@link CpuProfilerConfig} technology name.
   */
  public static String fromTaskTypeToConfigName(ProfilerTaskType taskType, @Nullable TaskHomeTabModel.TaskRecordingType recordingType) {
    String configName = "";
    switch (taskType) {
      case JAVA_KOTLIN_METHOD_RECORDING -> {
        if (recordingType == TaskHomeTabModel.TaskRecordingType.SAMPLED) {
          configName = SAMPLED_JAVA_CONFIG_NAME;
        }
        else if (recordingType == TaskHomeTabModel.TaskRecordingType.INSTRUMENTED) {
          configName = INSTRUMENTED_JAVA_CONFIG_NAME;
        }
      }
      case CALLSTACK_SAMPLE -> configName = SAMPLED_NATIVE_CONFIG_NAME;
      case SYSTEM_TRACE  -> configName = SYSTEM_TRACE_CONFIG_NAME;
      case NATIVE_ALLOCATIONS -> configName = NATIVE_ALLOCATIONS_CONFIG_NAME;
    }
    return configName;
  }

  public static ProfilerTaskType fromTechnologyToTaskType(CpuProfilerConfig.Technology technology) {
    return switch (technology) {
      case SAMPLED_JAVA, INSTRUMENTED_JAVA -> ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING;
      case SAMPLED_NATIVE -> ProfilerTaskType.CALLSTACK_SAMPLE;
      case SYSTEM_TRACE -> ProfilerTaskType.SYSTEM_TRACE;
      case NATIVE_ALLOCATIONS -> ProfilerTaskType.NATIVE_ALLOCATIONS;
    };
  }
}
