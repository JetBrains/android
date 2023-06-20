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
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One of the stages the profiler tool goes through. It models a "state" in the profiler tool itself.
 *
 * @param <T> timeline type for this stage
 */
public abstract class Stage<T extends Timeline> extends AspectObserver {

  protected static final long PROFILING_INSTRUCTIONS_EASE_OUT_NS = TimeUnit.SECONDS.toNanos(3);

  private final StudioProfilers myProfilers;

  /**
   * The active tooltip for stages that contain more than one tooltips.
   */
  @Nullable
  private TooltipModel myTooltip;

  public Stage(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
  }

  @NotNull
  public StudioProfilers getStudioProfilers() {
    return myProfilers;
  }

  /**
   * @return the {@link Timeline} that this stage is based on.
   */
  @NotNull
  public abstract T getTimeline();

  abstract public void enter();

  abstract public void exit();

  /**
   * @return the stage enum for Studio feature tracker.
   */
  abstract public AndroidProfilerEvent.Stage getStageType();

  @Nullable
  public TooltipModel getTooltip() {
    return myTooltip;
  }

  /**
   * Changes the active tooltip to the given type.
   * @param tooltip
   */
  public void setTooltip(TooltipModel tooltip) {
    if (tooltip != null && myTooltip != null && tooltip.getClass().equals(myTooltip.getClass())) {
      return;
    }
    if (myTooltip != null) {
      myTooltip.dispose();
    }
    myTooltip = tooltip;
    getStudioProfilers().changed(ProfilerAspect.TOOLTIP);
  }

  /**
   * @return an instance of the stage that is this stage's direct parent.
   *         An example of where this is used is in a user interface when the user
   *         clicks the "back" button.
   */
  @NotNull
  public Stage<?> getParentStage() {
    return new StudioMonitorStage(getStudioProfilers());
  }

  /**
   * @return the class of this stage's "home" stage (i.e. its highest ancestor).
   *         An example of where this is used is in a user interface when the user
   *         clicks the "home" button.
   */
  @NotNull
  public Class<? extends Stage> getHomeStageClass() {
    return this.getClass();
  }

  /**
   * @return a message string to prompt user if they want to exit the stage,
   *         indicating any implication. Returns null if no such prompt is
   *         necessary.
   */
  @Nullable
  public String getConfirmExitMessage() {
    return null;
  }

  /**
   * @return whether this stage provides any interaction with the timeline,
   *         which hints the user interface to show or hide timeline-related functionalities
   */
  public boolean isInteractingWithTimeline() {
    return true;
  }

  /**
   * Log a message (e.g., to idea.log) indicating the entering of a profiler stage.
   */
  protected void logEnterStage() {
    LogUtils.logIfVerbose(myProfilers.getIdeServices(), this.getClass(), "Enter " + this.getClass().getSimpleName());
  }
}
