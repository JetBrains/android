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

package com.android.tools.adtui;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;

import java.util.concurrent.TimeUnit;

/**
 * Animates a time range assuming the data is represented in microseconds since epoch.
 */
public class AnimatedTimeRange implements Updatable {

  private final Range mRange;

  private boolean mShift;

  private long mOffsetUs;

  public AnimatedTimeRange(Range range, long offsetUs) {
    mRange = range;
    mOffsetUs = offsetUs;
  }

  /**
   * Whether the animation should shift the whole range.
   */
  public void setShift(boolean shift) {
    mShift = shift;
  }

  @Override
  public void update(long elapsedNs) {
    long now = TimeUnit.NANOSECONDS.toMicros(System.nanoTime()) - mOffsetUs;
    double min = mRange.getMin();
    double max = mRange.getMax();
    mRange.setMax(now);
    if (mShift) {
      mRange.setMin(min + (now - max));
    }
  }
}
