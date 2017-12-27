/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.model.AspectObserver;
import org.jetbrains.annotations.NotNull;

/**
 * One of the stages the profiler tool goes through. It models a "state" in the profiler tool itself.
 */
public abstract class Stage extends AspectObserver {

  private final StudioProfilers myProfilers;

  private ProfilerMode myProfilerMode = ProfilerMode.NORMAL;

  public Stage(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
  }

  public StudioProfilers getStudioProfilers() {
    return myProfilers;
  }

  abstract public void enter();

  abstract public void exit();

  @NotNull
  public final ProfilerMode getProfilerMode() { return myProfilerMode; }

  /**
   * Allow inheriting classes to modify the {@link ProfilerMode}.
   *
   * Note that this method is intentionally not public, as only the stages themselves should
   * contain the logic for setting their profiler mode. If a view finds itself needing to
   * toggle the profiler mode, it should do it indirectly, either by modifying a model class
   * inside the stage or by calling a public method on the stage which changes the mode as a
   * side effect.
   */
  protected final void setProfilerMode(@NotNull ProfilerMode profilerMode) {
    if (myProfilerMode != profilerMode) {
      myProfilerMode = profilerMode;
      getStudioProfilers().modeChanged();
    }
  }
}
