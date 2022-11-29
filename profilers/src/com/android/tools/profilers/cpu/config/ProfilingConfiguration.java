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
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Preferences set when start a profiling session.
 */
public abstract class ProfilingConfiguration implements OptionsProvider {
  public static final String DEFAULT_CONFIGURATION_NAME = "Unnamed";
  public static final int DEFAULT_BUFFER_SIZE_MB = 8;
  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;
  public static final String TRACE_CONFIG_GROUP = "Trace config";

  public enum AdditionalOptions {
    SYMBOL_DIRS,
    APP_PKG_NAME
  }

  public enum TraceType {
    ART ("Art"),
    ATRACE("Atrace"),
    SIMPLEPERF("Simpleperf"),
    PERFETTO("Perfetto"),
    UNSPECIFIED("Unspecified");

    @NotNull
    public static TraceType from(@NotNull Trace.TraceConfiguration config) {
      if (config.hasArtOptions()) {
        return ART;
      }
      else if (config.hasAtraceOptions()) {
        return ATRACE;
      }
      else if (config.hasSimpleperfOptions()) {
        return SIMPLEPERF;
      }
      else if (config.hasPerfettoOptions()) {
        return PERFETTO;
      }
      else {
        return UNSPECIFIED;
      }
    }

    @NotNull private final String myDisplayName;

    TraceType(@NotNull String displayName) {
      myDisplayName = displayName;
    }

    @NotNull
    public String getDisplayName() {
      return myDisplayName;
    }
  }

  // TODO (b/258542374): Delete this mapping once UserOptions is removed and thus so is TraceType proto.
  public static final Map<TraceType, Trace.UserOptions.TraceType> TRACE_TYPE_MAP = Map.of(
    TraceType.ART, Trace.UserOptions.TraceType.ART,
    TraceType.ATRACE, Trace.UserOptions.TraceType.ATRACE,
    TraceType.SIMPLEPERF, Trace.UserOptions.TraceType.SIMPLEPERF,
    TraceType.PERFETTO, Trace.UserOptions.TraceType.PERFETTO,
    TraceType.UNSPECIFIED, Trace.UserOptions.TraceType.UNSPECIFIED_TYPE
  );

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
    switch (proto.getUnionCase()) {
      case ART_OPTIONS:
        if (proto.getArtOptions().getTraceMode() == Trace.TraceMode.SAMPLED) {
          ArtSampledConfiguration artSampled = new ArtSampledConfiguration("");
          artSampled.setProfilingSamplingIntervalUs(proto.getArtOptions().getSamplingIntervalUs());
          artSampled.setProfilingBufferSizeInMb(proto.getArtOptions().getBufferSizeInMb());
          return artSampled;
        }
        else {
          ArtInstrumentedConfiguration artInstrumented = new ArtInstrumentedConfiguration("");
          artInstrumented.setProfilingBufferSizeInMb(proto.getArtOptions().getBufferSizeInMb());
          return artInstrumented;
        }
      case PERFETTO_OPTIONS:
        PerfettoConfiguration perfetto = new PerfettoConfiguration("");
        if (proto.getPerfettoOptions().getBuffersCount() > 0) {
          // Perfetto buffer size is configured for the first buffer always. Value is fetched as Kb, so we convert to Mb.
          perfetto.setProfilingBufferSizeInMb(proto.getPerfettoOptions().getBuffers(0).getSizeKb() / 1024);
        }
        return perfetto;
      case ATRACE_OPTIONS:
        AtraceConfiguration atrace = new AtraceConfiguration("");
        atrace.setProfilingBufferSizeInMb(proto.getAtraceOptions().getBufferSizeInMb());
        return atrace;
      case SIMPLEPERF_OPTIONS:
        SimpleperfConfiguration simpleperf = new SimpleperfConfiguration("");
        simpleperf.setProfilingSamplingIntervalUs(proto.getSimpleperfOptions().getSamplingIntervalUs());
        return simpleperf;
      case UNION_NOT_SET:
        // fall through
      default:
        return new UnspecifiedConfiguration(DEFAULT_CONFIGURATION_NAME);
    }
  }

  /**
   * Converts {@code this} to {@link Trace.UserOptions}.
   */
  @NotNull
  public Trace.UserOptions toProto() {
    return buildUserOptions()
      .setName(getName())
      .setTraceType(TRACE_TYPE_MAP.get(getTraceType()))
      .build();
  }

  protected abstract Trace.UserOptions.Builder buildUserOptions();

  /**
   * Returns an options proto (field of {@link Trace.TraceConfiguration}) equivalent of the ProfilingConfiguration
   */
  protected abstract GeneratedMessageV3 getOptions();

  /**
   * Adds/sets the options field of a {@link Trace.TraceConfiguration} with proto conversion of {@link ProfilingConfiguration}
   * The additional options are a property bag for additional fields that should be set during TraceConfiguration creation.
   */
  public abstract void addOptions(Trace.TraceConfiguration.Builder configBuilder,
                                  Map<AdditionalOptions, ? extends Object> additionalOptions);

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
