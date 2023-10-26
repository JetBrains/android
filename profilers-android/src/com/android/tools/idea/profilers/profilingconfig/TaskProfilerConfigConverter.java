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

import static com.android.tools.profilers.cpu.config.ProfilingConfiguration.SYSTEM_TRACE_BUFFER_SIZE_MB;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.profiler.TaskSettingConfig;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.AtraceConfiguration;
import com.android.tools.profilers.cpu.config.PerfettoConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration;
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;

public class TaskProfilerConfigConverter {

  private TaskProfilerConfigConverter() { }

  /**
   * Converts from a {@link ProfilingConfiguration} to a {@link TaskSettingConfig}
   */
  public static TaskSettingConfig fromProfilingConfiguration(ProfilingConfiguration config) {
    TaskSettingConfig taskSettingConfig = null;

    switch (config.getTraceType()) {
      case ART:
        if (config instanceof ArtSampledConfiguration) {
          ArtSampledConfiguration artSampledConfiguration = (ArtSampledConfiguration)config;
          taskSettingConfig = new TaskSettingConfig(artSampledConfiguration.getName(), TaskSettingConfig.Technology.SAMPLED_JAVA);
          taskSettingConfig.setSamplingIntervalUs(artSampledConfiguration.getProfilingSamplingIntervalUs());
          taskSettingConfig.setBufferSizeMb(artSampledConfiguration.getProfilingBufferSizeInMb());
        }
        else {
          ArtInstrumentedConfiguration artInstrumentedConfiguration = (ArtInstrumentedConfiguration)config;
          taskSettingConfig = new TaskSettingConfig(artInstrumentedConfiguration.getName(), TaskSettingConfig.Technology.INSTRUMENTED_JAVA);
          taskSettingConfig.setBufferSizeMb(artInstrumentedConfiguration.getProfilingBufferSizeInMb());
        }
        break;
      case SIMPLEPERF:
        SimpleperfConfiguration simpleperfConfiguration = (SimpleperfConfiguration)config;
        taskSettingConfig = new TaskSettingConfig(simpleperfConfiguration.getName(), TaskSettingConfig.Technology.SAMPLED_NATIVE);
        taskSettingConfig.setSamplingIntervalUs(simpleperfConfiguration.getProfilingSamplingIntervalUs());
        break;
      case ATRACE:
        AtraceConfiguration atraceConfiguration = (AtraceConfiguration)config;
        taskSettingConfig = new TaskSettingConfig(atraceConfiguration.getName(), TaskSettingConfig.Technology.SYSTEM_TRACE);
        taskSettingConfig.setBufferSizeMb(SYSTEM_TRACE_BUFFER_SIZE_MB);
        break;
      case PERFETTO:
        PerfettoConfiguration perfettoConfiguration = (PerfettoConfiguration)config;
        taskSettingConfig = new TaskSettingConfig(perfettoConfiguration.getName(), TaskSettingConfig.Technology.SYSTEM_TRACE);
        taskSettingConfig.setBufferSizeMb(SYSTEM_TRACE_BUFFER_SIZE_MB);
        break;
      case UNSPECIFIED:
        UnspecifiedConfiguration unspecifiedConfiguration = (UnspecifiedConfiguration)config;
        taskSettingConfig = new TaskSettingConfig(unspecifiedConfiguration.getName(), TaskSettingConfig.Technology.SAMPLED_JAVA);
        break;
    }

    return taskSettingConfig;
  }

  /**
   * Converts from a {@link TaskSettingConfig} to a {@link ProfilingConfiguration}
   */
  public static ProfilingConfiguration toProfilingConfiguration(TaskSettingConfig config, int deviceApi) {
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
            configuration = new PerfettoConfiguration(name, true);
            break;
          }
        }
        if (deviceApi >= AndroidVersion.VersionCodes.P) {
          configuration = new PerfettoConfiguration(name, false);
        }
        else {
          configuration = new AtraceConfiguration(name);
        }
        break;
    }

    return configuration;
  }

  /**
   * Converts from list of {@link TaskSettingConfig} to a list of {@link ProfilingConfiguration}
   */
  public static List<ProfilingConfiguration> toProfilingConfiguration(List<TaskSettingConfig> configs, int deviceApi) {
    return ContainerUtil.map(configs, config -> toProfilingConfiguration(config, deviceApi));
  }
}
