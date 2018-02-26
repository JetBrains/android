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

import com.android.sdklib.AndroidVersion;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartRequest;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Preferences set when start a profiling session.
 */
public class ProfilingConfiguration {
  public static final int DEFAULT_BUFFER_SIZE_MB = 8;

  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;

  public static final String ART_SAMPLED = "Sampled (Java)";

  public static final String ART_INSTRUMENTED = "Instrumented (Java)";

  public static final String SIMPLEPERF = "Sampled (Native)";

  public static final String ATRACE = "Atrace";

  private static List<ProfilingConfiguration> ourDefaultConfigurations;

  /**
   * Name to identify the profiling preference. It should be displayed in the preferences list.
   */
  private String myName;

  /**
   * Profiler type (ART or simpleperf).
   */
  private CpuProfilerType myProfilerType;

  /**
   * Profiling mode (Sampled or Instrumented).
   */
  private CpuProfiler.CpuProfilerConfiguration.Mode myMode;

  private int myProfilingBufferSizeInMb = DEFAULT_BUFFER_SIZE_MB;

  /**
   * Sampling interval (for sample-based profiling) in microseconds.
   */
  private int myProfilingSamplingIntervalUs = DEFAULT_SAMPLING_INTERVAL_US;

  /**
   * Used to determine if the config is a default config or a custom config.
   */
  private boolean myIsDefault = false;

  public ProfilingConfiguration() {
    // Default constructor to be used by CpuProfilingConfigService
  }

  public ProfilingConfiguration(String name,
                                CpuProfilerType profilerType,
                                CpuProfiler.CpuProfilerConfiguration.Mode mode) {
    this(name, profilerType, mode, false);
  }

  private ProfilingConfiguration(String name,
                                 CpuProfilerType profilerType,
                                 CpuProfiler.CpuProfilerConfiguration.Mode mode,
                                 boolean isDefault) {
    myName = name;
    myProfilerType = profilerType;
    myMode = mode;
    myIsDefault = isDefault;
  }

  public CpuProfiler.CpuProfilerConfiguration.Mode getMode() {
    return myMode;
  }

  public void setMode(CpuProfiler.CpuProfilerConfiguration.Mode mode) {
    myMode = mode;
  }

  public CpuProfilerType getProfilerType() {
    return myProfilerType;
  }

  public void setProfilerType(CpuProfilerType profilerType) {
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

  public boolean isDefault() {
    return myIsDefault;
  }

  public int getRequiredDeviceLevel() {
    switch (myProfilerType) {
      // Atrace and simpleperf are supported from Android 8.0 (O)
      case ATRACE:
      case SIMPLEPERF:
        return AndroidVersion.VersionCodes.O;
      default:
        return 0;
    }
  }

  public boolean isDeviceLevelSupported(int deviceLevel) {
    return deviceLevel >= getRequiredDeviceLevel();
  }

  public void setProfilingSamplingIntervalUs(int profilingSamplingIntervalUs) {
    myProfilingSamplingIntervalUs = profilingSamplingIntervalUs;
  }

  public static List<ProfilingConfiguration> getDefaultProfilingConfigurations() {
    if (ourDefaultConfigurations == null) {
      ProfilingConfiguration artSampled = new ProfilingConfiguration(ART_SAMPLED,
                                                                     CpuProfilerType.ART,
                                                                     CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED,
                                                                     true);
      ProfilingConfiguration artInstrumented = new ProfilingConfiguration(ART_INSTRUMENTED,
                                                                          CpuProfilerType.ART,
                                                                          CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED,
                                                                          true);
      ProfilingConfiguration simpleperf = new ProfilingConfiguration(SIMPLEPERF,
                                                                     CpuProfilerType.SIMPLEPERF,
                                                                     CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED,
                                                                     true);
      ProfilingConfiguration atrace = new ProfilingConfiguration(ATRACE,
                                                                 CpuProfilerType.ATRACE,
                                                                 CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED,
                                                                 true);
      ourDefaultConfigurations = ImmutableList.of(artSampled, artInstrumented, simpleperf, atrace);
    }
    return ourDefaultConfigurations;
  }
}
