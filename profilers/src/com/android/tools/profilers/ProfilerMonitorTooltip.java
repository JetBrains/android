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
package com.android.tools.profilers;

import org.jetbrains.annotations.NotNull;

/**
 * Base class for tooltip used in a {@link ProfilerMonitor}.
 * @param <M> the profiler monitor where the tooltip is used in.
 */
public abstract class ProfilerMonitorTooltip<M extends ProfilerMonitor> implements ProfilerTooltip {
  @NotNull
  private final M myMonitor;

  public ProfilerMonitorTooltip(@NotNull M monitor) {
    myMonitor = monitor;
  }

  @NotNull
  public M getMonitor() {
    return myMonitor;
  }
}
