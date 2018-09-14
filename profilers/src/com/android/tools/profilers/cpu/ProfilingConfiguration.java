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

import com.android.annotations.VisibleForTesting;
import com.android.sdklib.AndroidVersion;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerMode;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Preferences set when start a profiling session.
 */
public class ProfilingConfiguration {
  /**
   * Technology name used by ART Sampled configurations.
   */
  @VisibleForTesting
  public static final String ART_SAMPLED_NAME = "Java Method Sample Recording";

  /**
   * Technology name used by ART Instrumented configurations.
   */
  @VisibleForTesting
  static final String ART_INSTRUMENTED_NAME = "Java Method Trace Recording";

  /**
   * Technology name used by imported ART trace configurations. We need a special name for imported ART traces because we can't tell if the
   * trace was generated using sampling or instrumentation.
   */
  @VisibleForTesting
  static final String ART_UNSPECIFIED_NAME = "Java Method Recording";

  /**
   * Technology name used by simpleperf configurations.
   */
  @VisibleForTesting
  public static final String SIMPLEPERF_NAME = "C/C++ Function Recording";

  /**
   * Technology name used by atrace configurations.
   */
  @VisibleForTesting
  public static final String ATRACE_NAME = "System Trace Recording";

  public static final int DEFAULT_BUFFER_SIZE_MB = 8;

  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;

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
  private CpuProfilerMode myMode;

  private int myProfilingBufferSizeInMb = DEFAULT_BUFFER_SIZE_MB;

  /**
   * Sampling interval (for sample-based profiling) in microseconds.
   */
  private int myProfilingSamplingIntervalUs = DEFAULT_SAMPLING_INTERVAL_US;

  public ProfilingConfiguration() {
    // Default constructor to be used by CpuProfilingConfigService
  }

  public ProfilingConfiguration(String name,
                                CpuProfilerType profilerType,
                                CpuProfilerMode mode) {
    myName = name;
    myProfilerType = profilerType;
    myMode = mode;
  }

  public CpuProfilerMode getMode() {
    return myMode;
  }

  public void setMode(CpuProfilerMode mode) {
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

  public int getRequiredDeviceLevel() {
    switch (myProfilerType) {
      // Atrace is supported from Android 4.1 (J) minimum, however the trace events changed in Android 7.0 (M).
      // For more info see b/79212883.
      case ATRACE:
        return AndroidVersion.VersionCodes.M;
      // Simpleperf is supported from Android 8.0 (O)
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

  /**
   * Converts from {@link com.android.tools.profiler.proto.CpuProfiler.CpuProfilerConfiguration} to {@link ProfilingConfiguration}.
   */
  @NotNull
  public static ProfilingConfiguration fromProto(@NotNull CpuProfiler.CpuProfilerConfiguration proto) {
    ProfilingConfiguration configuration = new ProfilingConfiguration(proto.getName(), proto.getProfilerType(), proto.getProfilerMode());
    configuration.setProfilingSamplingIntervalUs(proto.getSamplingIntervalUs());
    configuration.setProfilingBufferSizeInMb(proto.getBufferSizeInMb());
    return configuration;
  }

  /**
   * Converts {@code this} to {@link com.android.tools.profiler.proto.CpuProfiler.CpuProfilerConfiguration}.
   */
  @NotNull
  public CpuProfiler.CpuProfilerConfiguration toProto() {
    return CpuProfiler.CpuProfilerConfiguration.newBuilder()
      .setName(getName())
      .setProfilerType(getProfilerType())
      .setProfilerMode(getMode())
      .setSamplingIntervalUs(getProfilingSamplingIntervalUs())
      .setBufferSizeInMb(getProfilingBufferSizeInMb()).build();
  }

  /**
   * Returns the technology name corresponding to the given {@link CpuProfilerType} and {@link CpuProfilerMode}.
   */
  public static String getTechnologyName(CpuProfilerType profilerType, CpuProfilerMode profilerMode) {
    switch (profilerType) {
      case ART:
        if (profilerMode == CpuProfilerMode.SAMPLED) {
          return ART_SAMPLED_NAME;
        }
        else if (profilerMode == CpuProfilerMode.INSTRUMENTED) {
          return ART_INSTRUMENTED_NAME;
        }
        else {
          // We don't set the profiler mode to SAMPLED nor INSTRUMENTED when importing an ART trace, therefore we use a more generic name.
          return ART_UNSPECIFIED_NAME;
        }
      case SIMPLEPERF:
        return SIMPLEPERF_NAME;
      case ATRACE:
        return ATRACE_NAME;
      default:
        throw new IllegalStateException("Error while trying to get the name of an unknown profiling configuration");
    }
  }

  public static String getTechnologyName(ProfilingConfiguration configuration) {
    return getTechnologyName(configuration.getProfilerType(), configuration.getMode());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ProfilingConfiguration)) {
      return false;
    }
    ProfilingConfiguration incoming = (ProfilingConfiguration)obj;
    return StringUtil.equals(getName(), incoming.getName()) &&
           getProfilerType() == incoming.getProfilerType() &&
           getMode() == incoming.getMode() &&
           getProfilingSamplingIntervalUs() == incoming.getProfilingSamplingIntervalUs() &&
           getProfilingBufferSizeInMb() == incoming.getProfilingBufferSizeInMb();
  }

  @Override
  public int hashCode() {
    int hashCode = 0;
    if (getName() != null) {
      hashCode = getName().hashCode();
    }
    if (getProfilerType() != null) {
      hashCode ^= getProfilerType().hashCode();
    }
    if (getMode() != null) {
      hashCode ^= getMode().hashCode();
    }
    hashCode ^= getProfilingSamplingIntervalUs();
    hashCode ^= getProfilingBufferSizeInMb();
    return hashCode;
  }
}
