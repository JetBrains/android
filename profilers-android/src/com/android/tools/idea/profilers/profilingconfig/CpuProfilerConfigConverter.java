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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.AtraceConfiguration;
import com.android.tools.profilers.cpu.config.PerfettoConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration;
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;

public class CpuProfilerConfigConverter {

  private CpuProfilerConfigConverter() {}


  /**
   * Converts from a {@link ProfilingConfiguration} to a {@link CpuProfilerConfig}
   */
  public static CpuProfilerConfig fromProfilingConfiguration(ProfilingConfiguration config) {
    CpuProfilerConfig cpuProfilerConfig = null;

    switch (config.getTraceType()) {
      case ART:
        if(config instanceof ArtSampledConfiguration) {
          ArtSampledConfiguration artSampledConfiguration = (ArtSampledConfiguration) config;
          cpuProfilerConfig = new CpuProfilerConfig(artSampledConfiguration.getName(), CpuProfilerConfig.Technology.SAMPLED_JAVA);
          cpuProfilerConfig.setSamplingIntervalUs(artSampledConfiguration.getProfilingSamplingIntervalUs());
          cpuProfilerConfig.setBufferSizeMb(artSampledConfiguration.getProfilingBufferSizeInMb());
        }
        else {
          ArtInstrumentedConfiguration artInstrumentedConfiguration = (ArtInstrumentedConfiguration) config;
          cpuProfilerConfig = new CpuProfilerConfig(artInstrumentedConfiguration.getName(), CpuProfilerConfig.Technology.INSTRUMENTED_JAVA);
          cpuProfilerConfig.setBufferSizeMb(artInstrumentedConfiguration.getProfilingBufferSizeInMb());
        }
        break;
      case SIMPLEPERF:
        SimpleperfConfiguration simpleperfConfiguration = (SimpleperfConfiguration) config;
        cpuProfilerConfig = new CpuProfilerConfig(simpleperfConfiguration.getName(), CpuProfilerConfig.Technology.SAMPLED_NATIVE);
        cpuProfilerConfig.setSamplingIntervalUs(simpleperfConfiguration.getProfilingSamplingIntervalUs());
        break;
      case ATRACE:
        AtraceConfiguration atraceConfiguration = (AtraceConfiguration) config;
        cpuProfilerConfig = new CpuProfilerConfig(atraceConfiguration.getName(), CpuProfilerConfig.Technology.SYSTEM_TRACE);
        cpuProfilerConfig.setBufferSizeMb(atraceConfiguration.getProfilingBufferSizeInMb());
        break;
      case PERFETTO:
        PerfettoConfiguration perfettoConfiguration = (PerfettoConfiguration) config;
        cpuProfilerConfig = new CpuProfilerConfig(perfettoConfiguration.getName(), CpuProfilerConfig.Technology.SYSTEM_TRACE);
        cpuProfilerConfig.setBufferSizeMb(perfettoConfiguration.getProfilingBufferSizeInMb());
        break;
      case UNSPECIFIED_TYPE:
        // fall through
      case UNRECOGNIZED:
        UnspecifiedConfiguration unspecifiedConfiguration = (UnspecifiedConfiguration) config;
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
        ((ArtSampledConfiguration) configuration).setProfilingBufferSizeInMb(config.getBufferSizeMb());
        ((ArtSampledConfiguration) configuration).setProfilingSamplingIntervalUs(config.getSamplingIntervalUs());
        break;
      case INSTRUMENTED_JAVA:
        configuration = new ArtInstrumentedConfiguration(name);
        ((ArtInstrumentedConfiguration) configuration).setProfilingBufferSizeInMb(config.getBufferSizeMb());
        break;
      case SAMPLED_NATIVE:
        configuration = new SimpleperfConfiguration(name);
        ((SimpleperfConfiguration) configuration).setProfilingSamplingIntervalUs(config.getSamplingIntervalUs());
        break;
      case SYSTEM_TRACE:
        if(deviceApi >= AndroidVersion.VersionCodes.P) {
          configuration = new PerfettoConfiguration(name);
          ((PerfettoConfiguration) configuration).setProfilingBufferSizeInMb(config.getBufferSizeMb());
        }
        else {
          configuration = new AtraceConfiguration(name);
          ((AtraceConfiguration) configuration).setProfilingBufferSizeInMb(config.getBufferSizeMb());
        }
        break;
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
   * Converts from list of {@link CpuProfilerConfig} to list of {@link Trace.TraceConfiguration}
   */
  public static List<Trace.UserOptions> toProto(List<CpuProfilerConfig> configs, int deviceApi) {
    return ContainerUtil.map(configs, config -> toProto(config, deviceApi));
  }

  /**
   * Converts from {@link CpuProfilerConfig} to {@link Trace.UserOptions}
   */
  public static Trace.UserOptions toProto(CpuProfilerConfig config, int deviceApi) {
    Trace.UserOptions.Builder protoBuilder = Trace.UserOptions.newBuilder()
      .setName(config.getName())
      .setBufferSizeInMb(config.getBufferSizeMb())
      .setSamplingIntervalUs(config.getSamplingIntervalUs());

    switch (config.getTechnology()) {
      case SAMPLED_JAVA:
        protoBuilder.setTraceType(Trace.UserOptions.TraceType.ART);
        protoBuilder.setTraceMode(Trace.TraceMode.SAMPLED);
        break;
      case INSTRUMENTED_JAVA:
        protoBuilder.setTraceType(Trace.UserOptions.TraceType.ART);
        protoBuilder.setTraceMode(Trace.TraceMode.INSTRUMENTED);
        break;
      case SAMPLED_NATIVE:
        protoBuilder.setTraceType(Trace.UserOptions.TraceType.SIMPLEPERF);
        protoBuilder.setTraceMode(Trace.TraceMode.SAMPLED);
        break;
      case SYSTEM_TRACE:
        if (deviceApi >= AndroidVersion.VersionCodes.P) {
          protoBuilder.setTraceType(Trace.UserOptions.TraceType.PERFETTO);
        } else {
          protoBuilder.setTraceType(Trace.UserOptions.TraceType.ATRACE);
        }
        protoBuilder.setTraceMode(Trace.TraceMode.INSTRUMENTED);
        break;
    }

    return protoBuilder.build();
  }

  /**
   * Converts from {@link Trace.UserOptions} to {@link CpuProfilerConfig}
   */
  public static CpuProfilerConfig fromProto(Trace.UserOptions proto) {
    CpuProfilerConfig config = new CpuProfilerConfig()
      .setName(proto.getName())
      .setSamplingIntervalUs(proto.getSamplingIntervalUs())
      .setBufferSizeMb(proto.getBufferSizeInMb());

    switch (proto.getTraceType()) {
      case ART:
        if (proto.getTraceMode() == Trace.TraceMode.SAMPLED) {
          config.setTechnology(CpuProfilerConfig.Technology.SAMPLED_JAVA);
        }
        else {
          config.setTechnology(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA);
        }
        break;
      case SIMPLEPERF:
        config.setTechnology(CpuProfilerConfig.Technology.SAMPLED_NATIVE);
        break;
      case ATRACE: // fall-through
      case PERFETTO:
        config.setTechnology(CpuProfilerConfig.Technology.SYSTEM_TRACE);
        break;
      default:
        throw new IllegalArgumentException("Unsupported trace type: " + proto.getTraceType());
    }
    return config;
  }
}
