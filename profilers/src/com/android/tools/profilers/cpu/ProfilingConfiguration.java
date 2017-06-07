/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.profiler.proto.CpuProfiler;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Preferences set when start a profiling session.
 */
public class ProfilingConfiguration {

  public static final int DEFAULT_BUFFER_SIZE_MB = 8;

  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;

  // TODO: change ART sampled configuration name to Sampled (Java) when we enable simpleperf flag on
  public static final String ART_SAMPLED = "Sampled";

  public static final String ART_INSTRUMENTED = "Instrumented";

  public static final String SIMPLEPERF = "Sampled (Hybrid)";

  private static List<ProfilingConfiguration> ourDefaultConfigurations;

  /**
   * Name to identify the profiling preference. It should be displayed in the preferences list.
   */
  private String myName;

  /**
   * Profiler type (ART or simpleperf).
   */
  private CpuProfiler.CpuProfilerType myProfilerType;

  /**
   * Profiling mode (Sampled or Instrumented).
   */
  private CpuProfiler.CpuProfilingAppStartRequest.Mode myMode;

  private int myProfilingBufferSizeInMb = DEFAULT_BUFFER_SIZE_MB;

  /**
   * Sampling interval (for sample-based profiling) in microseconds.
   */
  private int myProfilingSamplingIntervalUs = DEFAULT_SAMPLING_INTERVAL_US;

  public ProfilingConfiguration() {
    // Default constructor to be used by CpuProfilingConfigService
  }

  public ProfilingConfiguration(String name,
                                CpuProfiler.CpuProfilerType profilerType,
                                CpuProfiler.CpuProfilingAppStartRequest.Mode mode) {
    myName = name;
    myProfilerType = profilerType;
    myMode = mode;
  }

  public CpuProfiler.CpuProfilingAppStartRequest.Mode getMode() {
    return myMode;
  }

  public void setMode(CpuProfiler.CpuProfilingAppStartRequest.Mode mode) {
    myMode = mode;
  }

  public CpuProfiler.CpuProfilerType getProfilerType() {
    return myProfilerType;
  }

  public void setProfilerType(CpuProfiler.CpuProfilerType profilerType) {
    myProfilerType = profilerType;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public int getProfilingBufferSizeInMb() {
    return myProfilingBufferSizeInMb;
  }

  public void setProfilingBufferSizeInMb(int profilingBufferSizeInMb) {
    myProfilingBufferSizeInMb = profilingBufferSizeInMb;
  }

  public int getProfilingSamplingIntervalUs() {
    return myProfilingSamplingIntervalUs;
  }

  public void setProfilingSamplingIntervalUs(int profilingSamplingIntervalUs) {
    myProfilingSamplingIntervalUs = profilingSamplingIntervalUs;
  }

  public static List<ProfilingConfiguration> getDefaultProfilingConfigurations() {
    if (ourDefaultConfigurations == null) {
      ProfilingConfiguration artSampled = new ProfilingConfiguration(ART_SAMPLED,
                                                                     CpuProfiler.CpuProfilerType.ART,
                                                                     CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
      ProfilingConfiguration artInstrumented = new ProfilingConfiguration(ART_INSTRUMENTED,
                                                                          CpuProfiler.CpuProfilerType.ART,
                                                                          CpuProfiler.CpuProfilingAppStartRequest.Mode.INSTRUMENTED);
      ProfilingConfiguration simpleperf = new ProfilingConfiguration(SIMPLEPERF,
                                                                     CpuProfiler.CpuProfilerType.SIMPLE_PERF,
                                                                     CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
      ourDefaultConfigurations = ImmutableList.of(artSampled, artInstrumented, simpleperf);
    }
    return ourDefaultConfigurations;
  }
}
