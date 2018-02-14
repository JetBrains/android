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
package com.android.tools.idea.run;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class StartupCpuProfilingConfiguration {
  public static final int DEFAULT_SAMPLING_INTERVAL_US = 1000;

  public static final List<StartupCpuProfilingConfiguration> DEFAULT_CONFIGS = ImmutableList.of(
    new StartupCpuProfilingConfiguration(Technology.SAMPLED_JAVA),
    new StartupCpuProfilingConfiguration(Technology.INSTRUMENTED_JAVA),
    new StartupCpuProfilingConfiguration(Technology.SAMPLED_NATIVE)
  );

  @NotNull private final String myName;
  @NotNull private final Technology myTechnology;
  private final int mySamplingIntervalUs;

  private StartupCpuProfilingConfiguration(@NotNull String name, @NotNull Technology technology, int samplingInterval) {
    myName = name;
    myTechnology = technology;
    mySamplingIntervalUs = samplingInterval;
  }

  private StartupCpuProfilingConfiguration(@NotNull Technology technology) {
    this(technology.getName(), technology, DEFAULT_SAMPLING_INTERVAL_US);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Technology getTechnology() {
    return myTechnology;
  }

  public int getSamplingInterval() {
    return mySamplingIntervalUs;
  }

  @Nullable
  public static StartupCpuProfilingConfiguration getDefaultConfigByName(@NotNull String name) {
    for (StartupCpuProfilingConfiguration config: DEFAULT_CONFIGS) {
      if (name.equals(config.getName())) {
        return config;
      }
    }
    return null;
  }

  // TODO: unify with {@link ProfilingConfiguration}, b/73470862.
  public enum Technology {
    SAMPLED_JAVA {
      @NotNull
      @Override
      public String getName() {
        return "Sampled (Java)";
      }
    },
    INSTRUMENTED_JAVA {
      @NotNull
      @Override
      public String getName() {
        return "Instrumented (Java)";
      }
    },
    SAMPLED_NATIVE {
      @NotNull
      @Override
      public String getName() {
        return "Sampled (Native)";
      }
    };

    @NotNull
    public abstract String getName();
  }
}
