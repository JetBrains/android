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
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Preferences set when start a profiling session.
 */
public class ProfilingConfiguration {
  public static final int DEFAULT_BUFFER_SIZE_MB = 8;

  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;

  public static final String ART_SAMPLED = "Sampled (Java)";

  public static final String ART_INSTRUMENTED = "Instrumented (Java)";

  /**
   * Default name used by ART configurations (both sampled and instrumented).
   * TODO(b/76152657): when getDefaultConfigName supports both mode and profiler type, remove this field.
   */
  @VisibleForTesting
  static final String ART = "Java";

  public static final String SIMPLEPERF = "Sampled (Native)";

  public static final String ATRACE = "System Trace";

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

  public ProfilingConfiguration() {
    // Default constructor to be used by CpuProfilingConfigService
  }

  public ProfilingConfiguration(String name,
                                CpuProfilerType profilerType,
                                CpuProfiler.CpuProfilerConfiguration.Mode mode) {
    myName = name;
    myProfilerType = profilerType;
    myMode = mode;
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

  /**
   * Returns the default configuration name corresponding to the given {@link CpuProfilerType}.
   * TODO(b/76152657): support profiler Mode to differentiate Sampled and Instrumented captures. In order to do that, we should probably
   *                   change our API to add a CpuProfilerConfiguration.Mode field to TraceInfo.
   */
  public static String getDefaultConfigName(CpuProfilerType profilerType) {
    switch (profilerType) {
      case ART:
        return ART;
      case SIMPLEPERF:
        return SIMPLEPERF;
      case ATRACE:
        return ATRACE;
      default:
        return "Unknown Configuration";
    }
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

  /**
   * Converts from {@link com.android.tools.profiler.proto.CpuProfiler.CpuProfilerConfiguration} to {@link ProfilingConfiguration}.
   */
  @NotNull
  public static ProfilingConfiguration fromProto(@NotNull CpuProfiler.CpuProfilerConfiguration proto) {
    ProfilingConfiguration configuration = new ProfilingConfiguration(proto.getName(), proto.getProfilerType(), proto.getMode());
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
      .setMode(getMode())
      .setSamplingIntervalUs(getProfilingSamplingIntervalUs())
      .setBufferSizeInMb(getProfilingBufferSizeInMb()).build();
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
