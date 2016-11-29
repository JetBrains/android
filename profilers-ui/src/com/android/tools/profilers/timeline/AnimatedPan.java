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
package com.android.tools.profilers.timeline;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.ProfilerTimeline;
import org.jetbrains.annotations.NotNull;

/**
 * A helper {@link Animatable} object that handles smooth panning.
 */
public final class AnimatedPan implements Animatable {

  private static final float PAN_LERP_FRACTION = 0.5f;
  private static final float PAN_LERP_THRESHOLD_US = 1f;
  private static final double EPSILON = 1e-9;

  @NotNull private Choreographer myChoreographer;
  @NotNull private final ProfilerTimeline myTimeline;

  private double myRemainingDeltaUs;

  /**
   * @param choreographer The {@link Choreographer} instance this object is registered to. This is used for unregistering this object
   *                      when the animation is finished.
   * @param timeline      The timeline object to modify.
   * @param panDeltaUs    The total distance(time) this panning animation will cover.
   */
  public AnimatedPan(@NotNull Choreographer choreographer, @NotNull ProfilerTimeline timeline, double panDeltaUs) {
    myChoreographer = choreographer;
    myTimeline = timeline;

    Range viewRange = myTimeline.getViewRange();
    Range dataRange = myTimeline.getDataRange();
    if (viewRange.getLength() > dataRange.getLength()) {
      // Panning not allowed if view range is longer than the data range.
      // This handles the special case where the view range starts before the data range at the beginning of a profiling session.
      myRemainingDeltaUs = 0;
    }
    else {
      myRemainingDeltaUs = panDeltaUs;
      myTimeline.setStreaming(false);
    }

    myTimeline.incrementStreamLock();
  }

  @Override
  public void animate(float frameLength) {
    if (myRemainingDeltaUs == 0) {
      myChoreographer.unregister(this);
      myTimeline.decrementStreamLock();
      return;
    }

    // Calculate the total amount of delta to pan, snaps if the delta is less than a certain threshold.
    double deltaUs = Choreographer.lerp(myRemainingDeltaUs, 0, PAN_LERP_FRACTION, frameLength);
    if (PAN_LERP_THRESHOLD_US > Math.abs(deltaUs)) {
      deltaUs = myRemainingDeltaUs;
      myRemainingDeltaUs = 0;
    }
    else {
      myRemainingDeltaUs -= deltaUs;
    }

    Range viewRange = myTimeline.getViewRange();
    if (deltaUs < 0) {
      // Moving left - clamp to data min and stops further animation.
      double unclampedMin = viewRange.getMin() + deltaUs;
      double minUs = myTimeline.clampToDataRange(unclampedMin);
      deltaUs = minUs - viewRange.getMin();
      if (unclampedMin < minUs - EPSILON) {
        myRemainingDeltaUs = 0;
      }
    }
    else {
      // Moving right - clamp to data max and stops further animation.
      double unclampedMax = viewRange.getMax() + deltaUs;
      double maxUs = myTimeline.clampToDataRange(unclampedMax);
      deltaUs = maxUs - viewRange.getMax();
      if (unclampedMax > maxUs + EPSILON) {
        myRemainingDeltaUs = 0;
      }
    }

    viewRange.shift(deltaUs);
  }
}
