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

import com.android.tools.profiler.proto.CpuProfiler;
import org.jetbrains.annotations.NotNull;

public enum ProfilingTechnology {
  ART_SAMPLED("Java Method Sample Recording",
              "Samples Java code using Android Runtime"),

  ART_INSTRUMENTED("Java Method Trace Recording",
                   "Instruments Java code using Android Runtime"),

  // This technology used by imported ART Trace configurations.
  // "Unspecified" because there is no way of telling if the trace was generated using sampling or instrumentations.
  ART_UNSPECIFIED("Java Method Recording",
                  "Profiles Java code using Android Runtime"),

  SIMPLEPERF("C/C++ Function Recording",
             "Samples native code using simpleperf"),

  ATRACE("System Trace Recording",
         "Traces Java and native code at the Android platform level");

  @NotNull private final String myName;
  @NotNull private final String myDescription;

  ProfilingTechnology(@NotNull String name, @NotNull String description) {
    myName = name;
    myDescription = description;
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
  public static ProfilingTechnology fromTypeAndMode(@NotNull CpuProfiler.CpuProfilerType type,
                                                    @NotNull CpuProfiler.CpuProfilerMode mode) {
    switch (type) {
      case ART:
        if (mode == CpuProfiler.CpuProfilerMode.SAMPLED) {
          return ART_SAMPLED;
        }
        else if (mode == CpuProfiler.CpuProfilerMode.INSTRUMENTED) {
          return ART_INSTRUMENTED;
        }
        else {
          return ART_UNSPECIFIED;
        }
      case SIMPLEPERF:
        return SIMPLEPERF;
      case ATRACE:
        return ATRACE;
      default:
        throw new IllegalStateException("Error while trying to get the name of an unknown profiling configuration");
    }
  }

  @NotNull
  public static ProfilingTechnology fromConfig(@NotNull ProfilingConfiguration config) {
    return fromTypeAndMode(config.getProfilerType(), config.getMode());
  }
}
