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
package com.android.tools.profilers.cpu;

import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import org.jetbrains.annotations.NotNull;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;

public enum ProfilingTechnology {
  ART_SAMPLED("Java/Kotlin Method Sample Recording (legacy)",
              "Samples Java/Kotlin code using Android Runtime."),

  ART_INSTRUMENTED("Java/Kotlin Method Trace Recording",
                   "Instruments Java/Kotlin code using Android Runtime, tracking every method call" +
                   " (this incurs high overhead making timing information inaccurate)."),

  // This technology used by imported ART Trace configurations.
  // "Unspecified" because there is no way of telling if the trace was generated using sampling or instrumentations.
  ART_UNSPECIFIED("Java/Kotlin Method Recording",
                  "Profiles method calls using Android Runtime."),

  SIMPLEPERF("Callstack Sample Recording",
             "Samples Java/Kotlin and native code using simpleperf.",
             "Available for Android 8.0 (API level 26) and higher."),

  SYSTEM_TRACE("System Trace Recording",
               "Traces Java/Kotlin and native code at the Android platform level.",
               "Available for Android 7.0 (API level 24) and higher.");

  @NotNull private final String myName;

  /**
   * Description of the technology, e.g it is used in {@code RecordingInitiatorPane}.
   */
  @NotNull private final String myDescription;

  /**
   * An extra context for the description of the technology, e.g it is used in {@code CpuProfilingConfigPane}.
   */
  @NotNull private final String myExtraDescription;

  ProfilingTechnology(@NotNull String name, @NotNull String description, @NotNull String extraDescription) {
    myName = name;
    myDescription = description;
    myExtraDescription = extraDescription;
  }

  ProfilingTechnology(@NotNull String name, @NotNull String description) {
    this(name, description, "");
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public String getLongDescription() {
    return String.format("<html>%s %s</html>", myDescription, myExtraDescription);
  }

  @NotNull
  public TraceType getType() {
    switch (this) {
      case ART_SAMPLED:
        return TraceType.ART;
      case ART_INSTRUMENTED:
        return TraceType.ART;
      case ART_UNSPECIFIED:
        return TraceType.ART;
      case SIMPLEPERF:
        return TraceType.SIMPLEPERF;
      case SYSTEM_TRACE:
        return TraceType.ATRACE;
    }
    throw new IllegalArgumentException("Unreachable code");
  }

  @NotNull
  public Trace.TraceMode getMode() {
    switch (this) {
      case ART_SAMPLED:
        return Trace.TraceMode.SAMPLED;
      case ART_INSTRUMENTED:
        return Trace.TraceMode.INSTRUMENTED;
      case ART_UNSPECIFIED:
        return Trace.TraceMode.UNSPECIFIED_MODE;
      case SIMPLEPERF:
        return Trace.TraceMode.SAMPLED;
      case SYSTEM_TRACE:
        return Trace.TraceMode.INSTRUMENTED;
    }
    throw new IllegalArgumentException("Unreachable code");
  }

  @NotNull
  public static ProfilingTechnology fromTypeAndMode(@NotNull TraceType type,
                                                    @NotNull Trace.TraceMode mode) {
    switch (type) {
      case ART:
        if (mode == Trace.TraceMode.SAMPLED) {
          return ART_SAMPLED;
        }
        else if (mode == Trace.TraceMode.INSTRUMENTED) {
          return ART_INSTRUMENTED;
        }
        else {
          return ART_UNSPECIFIED;
        }
      case SIMPLEPERF:
        return SIMPLEPERF;
      case ATRACE: // fall-through
      case PERFETTO:
        return SYSTEM_TRACE;
      default:
        throw new IllegalStateException("Error while trying to get the name of an unknown profiling configuration");
    }
  }

  @NotNull
  public static ProfilingTechnology fromConfig(@NotNull ProfilingConfiguration config) {
    switch (config.getTraceType()) {
      case ART:
        if (config instanceof ArtSampledConfiguration) {
          return ART_SAMPLED;
        }
        else if (config instanceof ArtInstrumentedConfiguration) {
          return ART_INSTRUMENTED;
        }
        else {
          return ART_UNSPECIFIED;
        }
      case SIMPLEPERF:
        return SIMPLEPERF;
      case ATRACE: // fall-through
      case PERFETTO:
        return SYSTEM_TRACE;
      default:
        throw new IllegalStateException("Error while trying to get the name of an unknown profiling configuration");
    }
  }
}
