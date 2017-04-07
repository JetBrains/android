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
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.ProfilerTimeline;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Handles timeline streaming functionality for the Profiler UI.
 */
public final class AnimatedTimeline implements Animatable {

  @NotNull private final ProfilerTimeline myTimeline;

  public AnimatedTimeline(@NotNull ProfilerTimeline timeline) {
    myTimeline = timeline;
  }

  @Override
  public void animate(float frameLength) {
    if (!myTimeline.isStreaming()) {
      return;
    }

    // Advances time by frameLength (up to current data max - buffer)
    float frameLengthUs = frameLength * TimeUnit.SECONDS.toMicros(1);
    Range viewRange = myTimeline.getViewRange();
    double viewMaxUs = viewRange.getMax();
    double deltaUs = myTimeline.clampToDataRange(viewMaxUs + frameLengthUs) - viewMaxUs;
    viewRange.shift(deltaUs);
  }
}
