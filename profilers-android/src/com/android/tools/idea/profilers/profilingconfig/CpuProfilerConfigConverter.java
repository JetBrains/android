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

import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.profiler.proto.CpuProfiler;

import java.util.List;
import java.util.stream.Collectors;

public class CpuProfilerConfigConverter {

  private CpuProfilerConfigConverter() {}

  /**
   * Converts from list of {@link CpuProfilerConfig} to list of {@link CpuProfiler.CpuProfilerConfiguration}
   */
  public static List<CpuProfiler.CpuProfilerConfiguration> toProto(List<CpuProfilerConfig> configs) {
    return configs.stream().map(CpuProfilerConfigConverter::toProto).collect(Collectors.toList());
  }

  /**
   * Converts from {@link CpuProfilerConfig} to {@link CpuProfiler.CpuProfilerConfiguration}
   */
  public static CpuProfiler.CpuProfilerConfiguration toProto(CpuProfilerConfig config) {
    CpuProfiler.CpuProfilerConfiguration.Builder protoBuilder = CpuProfiler.CpuProfilerConfiguration
      .newBuilder()
      .setName(config.getName())
      .setBufferSizeInMb(config.getBufferSizeMb())
      .setSamplingIntervalUs(config.getSamplingIntervalUs());

    switch (config.getTechnology()) {
      case SAMPLED_JAVA:
        protoBuilder.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        protoBuilder.setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED);
        break;
      case INSTRUMENTED_JAVA:
        protoBuilder.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        protoBuilder.setMode(CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED);
        break;
      case SAMPLED_NATIVE:
        protoBuilder.setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF);
        protoBuilder.setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED);
        break;
      case ATRACE:
        protoBuilder.setProfilerType(CpuProfiler.CpuProfilerType.ATRACE);
        protoBuilder.setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED);
        break;
    }

    return protoBuilder.build();
  }

  /**
   * Converts from {@link CpuProfiler.CpuProfilerConfiguration} to {@link CpuProfilerConfig}
   */
  public static CpuProfilerConfig fromProto(CpuProfiler.CpuProfilerConfiguration proto) {
    CpuProfilerConfig config = new CpuProfilerConfig()
      .setName(proto.getName())
      .setSamplingIntervalUs(proto.getSamplingIntervalUs())
      .setBufferSizeMb(proto.getBufferSizeInMb());

    switch (proto.getProfilerType()) {
      case ART:
        if (proto.getMode() == CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED) {
          config.setTechnology(CpuProfilerConfig.Technology.SAMPLED_JAVA);
        } else {
          config.setTechnology(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA);
        }
        break;
      case SIMPLEPERF:
        config.setTechnology(CpuProfilerConfig.Technology.SAMPLED_NATIVE);
        break;
      case ATRACE:
        config.setTechnology(CpuProfilerConfig.Technology.ATRACE);
        break;
      default:
        throw new IllegalArgumentException("Unsupported trace type: " + proto.getProfilerType());
    }
    return config;
  }
}
