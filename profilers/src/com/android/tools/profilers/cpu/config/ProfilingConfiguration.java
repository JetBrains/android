/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.config;

import com.android.tools.adtui.model.options.OptionsProvider;
import com.android.tools.adtui.model.options.OptionsProperty;
import com.android.tools.idea.protobuf.GeneratedMessageV3;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Trace.UserOptions.TraceType;
import org.jetbrains.annotations.NotNull;

/**
 * Preferences set when start a profiling session.
 */
public abstract class ProfilingConfiguration implements OptionsProvider {
  public static final String DEFAULT_CONFIGURATION_NAME = "Unnamed";
  public static final int DEFAULT_BUFFER_SIZE_MB = 8;
  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;
  public static final String TRACE_CONFIG_GROUP = "Trace config";

  /**
   * Name to identify the profiling preference. It should be displayed in the preferences list.
   */
  @NotNull
  private String myName;

  protected ProfilingConfiguration(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public abstract TraceType getTraceType();

  @NotNull
  @OptionsProperty(name = "Configuration name: ", group = TRACE_CONFIG_GROUP, order = 99)
  public String getName() {
    return myName;
  }

  @OptionsProperty
  public void setName(@NotNull String name) {
    myName = name;
  }

  public abstract int getRequiredDeviceLevel();

  public boolean isDeviceLevelSupported(int deviceLevel) {
    return deviceLevel >= getRequiredDeviceLevel();
  }

  /**
   * Converts from {@link Trace.TraceConfiguration} to {@link ProfilingConfiguration}.
   */
  @NotNull
  public static ProfilingConfiguration fromProto(@NotNull Trace.TraceConfiguration proto) {
    ProfilingConfiguration configuration = null;
    switch (proto.getUserOptions().getTraceType()) {
      case ART:
        if (proto.getUserOptions().getTraceMode() == Trace.TraceMode.SAMPLED) {
          ArtSampledConfiguration artSampled = new ArtSampledConfiguration(proto.getUserOptions().getName());
          artSampled.setProfilingSamplingIntervalUs(proto.getUserOptions().getSamplingIntervalUs());
          artSampled.setProfilingBufferSizeInMb(proto.getUserOptions().getBufferSizeInMb());
          configuration = artSampled;
        }
        else {
          ArtInstrumentedConfiguration art = new ArtInstrumentedConfiguration(proto.getUserOptions().getName());
          art.setProfilingBufferSizeInMb(proto.getUserOptions().getBufferSizeInMb());
          configuration = art;
        }
        break;
      case PERFETTO:
        PerfettoConfiguration perfetto = new PerfettoConfiguration(proto.getUserOptions().getName());
        perfetto.setProfilingBufferSizeInMb(proto.getUserOptions().getBufferSizeInMb());
        configuration = perfetto;
        break;
      case ATRACE:
        AtraceConfiguration atrace = new AtraceConfiguration(proto.getUserOptions().getName());
        atrace.setProfilingBufferSizeInMb(proto.getUserOptions().getBufferSizeInMb());
        configuration = atrace;
        break;
      case SIMPLEPERF:
        SimpleperfConfiguration simpleperf = new SimpleperfConfiguration(proto.getUserOptions().getName());
        simpleperf.setProfilingSamplingIntervalUs(proto.getUserOptions().getSamplingIntervalUs());
        configuration = simpleperf;
        break;
      case UNRECOGNIZED:
      case UNSPECIFIED_TYPE:
        return new UnspecifiedConfiguration(DEFAULT_CONFIGURATION_NAME);
    }
    return configuration;
  }

  /**
   * Converts {@code this} to {@link Trace.UserOptions}.
   */
  @NotNull
  public Trace.UserOptions toProto() {
    return buildUserOptions()
      .setName(getName())
      .setTraceType(getTraceType())
      .build();
  }

  protected abstract Trace.UserOptions.Builder buildUserOptions();

  /**
   * Returns an options proto (field of {@link Trace.TraceConfiguration}) equivalent of the ProfilingConfiguration
   */
  protected abstract GeneratedMessageV3 getOptions();

  /**
   * Adds/sets the options field of a {@link Trace.TraceConfiguration} with proto conversion of {@link ProfilingConfiguration}
   */
  public abstract void addOptions(Trace.TraceConfiguration.Builder configBuilder);

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ProfilingConfiguration)) {
      return false;
    }
    ProfilingConfiguration incoming = (ProfilingConfiguration)obj;
    return incoming.toProto().equals(toProto());
  }

  @Override
  public int hashCode() {
    return toProto().hashCode();
  }
}
