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
package com.android.tools.idea.run.profiler;

import com.android.utils.HashCodes;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class CpuProfilerConfig {
  public static final int DEFAULT_BUFFER_SIZE_MB = 8;
  public static final String SAMPLED_JAVA_CONFIG_NAME = "Java/Kotlin Method Sample (legacy)";
  public static final String INSTRUMENTED_JAVA_CONFIG_NAME = "Java/Kotlin Method Trace";
  public static final String SAMPLED_NATIVE_CONFIG_NAME = "Callstack Sample";
  public static final String SYSTEM_TRACE_CONFIG_NAME = "System Trace";
  public static final String NATIVE_ALLOCATIONS_CONFIG_NAME = "Native Allocations";

  @NotNull private String myName;
  @NotNull private Technology myTechnology;
  private int mySamplingIntervalUs = 1000;
  private int myBufferSizeMb = DEFAULT_BUFFER_SIZE_MB;
  private int mySamplingRateBytes = 2048;

  /**
   * Default constructor to be used by {@link CpuProfilerConfigsState}.
   */
  public CpuProfilerConfig() {
    myName = Technology.SAMPLED_JAVA.getName();
    myTechnology = Technology.SAMPLED_JAVA;
  }

  public CpuProfilerConfig(@NotNull String name, @NotNull Technology technology) {
    myName = name;
    myTechnology = technology;
  }

  /**
   * Creates a default configuration with the given {@param technology}.
   */
  CpuProfilerConfig(@NotNull Technology technology) {
    this(technology.getName(), technology);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public CpuProfilerConfig setName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public Technology getTechnology() {
    return myTechnology;
  }

  @NotNull
  public CpuProfilerConfig setTechnology(@NotNull Technology technology) {
    myTechnology = technology;
    return this;
  }

  public int getSamplingIntervalUs() {
    return mySamplingIntervalUs;
  }

  @NotNull
  public CpuProfilerConfig setSamplingIntervalUs(int samplingIntervalUs) {
    mySamplingIntervalUs = samplingIntervalUs;
    return this;
  }

  public int getBufferSizeMb() {
    return myBufferSizeMb;
  }

  @NotNull
  public CpuProfilerConfig setBufferSizeMb(int bufferSize) {
    myBufferSizeMb = bufferSize;
    return this;
  }

  public int getSamplingRateBytes() {
    return mySamplingRateBytes;
  }

  public CpuProfilerConfig setSamplingRateBytes(int samplingRateBytes) {
    mySamplingRateBytes = samplingRateBytes;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CpuProfilerConfig config = (CpuProfilerConfig)o;
    return mySamplingIntervalUs == config.mySamplingIntervalUs &&
           myBufferSizeMb == config.myBufferSizeMb &&
           Objects.equals(myName, config.myName) &&
           myTechnology == config.myTechnology &&
           mySamplingRateBytes == config.mySamplingRateBytes;
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(myName.hashCode(), myTechnology.hashCode(), mySamplingIntervalUs, myBufferSizeMb, mySamplingRateBytes);
  }

  public enum Technology {
    SAMPLED_JAVA {
      @NotNull
      @Override
      public String getName() {
        return SAMPLED_JAVA_CONFIG_NAME;
      }
    },
    INSTRUMENTED_JAVA {
      @NotNull
      @Override
      public String getName() {
        return INSTRUMENTED_JAVA_CONFIG_NAME;
      }
    },
    SAMPLED_NATIVE {
      @NotNull
      @Override
      public String getName() {
        return SAMPLED_NATIVE_CONFIG_NAME;
      }
    },
    SYSTEM_TRACE {
      @NotNull
      @Override
      public String getName() {
        return SYSTEM_TRACE_CONFIG_NAME;
      }
    },
    NATIVE_ALLOCATIONS {
      @NotNull
      @Override
      public String getName() {
        return NATIVE_ALLOCATIONS_CONFIG_NAME;
      }
    };

    @NotNull
    public abstract String getName();
  }
}
