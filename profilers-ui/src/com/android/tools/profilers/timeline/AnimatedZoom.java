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
 * A helper {@link Animatable} object that handles smooth zooming.
 */
public final class AnimatedZoom implements Animatable {

  private static final float ZOOM_LERP_FRACTION = 0.5f;
  private static final float ZOOM_LERP_THRESHOLD_US = 1f;

  @NotNull private final Choreographer myChoreographer;
  @NotNull private final ProfilerTimeline myTimeline;

  private double myRemainingDeltaUs;
  private double myAnchor;

  /**
   * @param choreographer The {@link Choreographer} instance this object is registered to. This is used for unregistering this object
   *                      when the animation is finished.
   * @param timeline      The timeline object to modify.
   * @param zoomDeltaUs   The total distance(time) this animation will zoom into/out of.
   * @param anchor        The point in percentage of the current view range where the zooming should anchor.
   *                      e.g. zooming will center around the anchor.
   */
  public AnimatedZoom(@NotNull Choreographer choreographer, @NotNull ProfilerTimeline timeline, double zoomDeltaUs, double anchor) {
    myChoreographer = choreographer;
    myTimeline = timeline;
    myRemainingDeltaUs = zoomDeltaUs;
    myAnchor = anchor;
  }

  @Override
  public void animate(float frameLength) {
    // Calculate the total amount of delta to zoom, snaps if the delta is less than a certain threshold.
    double deltaUs = Choreographer.lerp(myRemainingDeltaUs, 0, ZOOM_LERP_FRACTION, frameLength);
    if (ZOOM_LERP_THRESHOLD_US > Math.abs(deltaUs)) {
      deltaUs = myRemainingDeltaUs;
      myRemainingDeltaUs = 0;
    }
    else {
      myRemainingDeltaUs -= deltaUs;
    }

    // Define the total delta around the anchor point and update the timeline.
    // TODO interaction with streaming?
    Range viewRange = myTimeline.getViewRange();
    double minDeltaUs = deltaUs * myAnchor;
    double maxDeltaUs = deltaUs - minDeltaUs;
    viewRange.set(myTimeline.clampToDataRange(viewRange.getMin() - minDeltaUs),
                  myTimeline.clampToDataRange(viewRange.getMax() + maxDeltaUs));

    if (myRemainingDeltaUs == 0) {
      myChoreographer.unregister(this);
    }
  }
}
