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

/**
 * An {@link Range} object that interpolates to its min/max values.
 */
public class AnimatedRange extends Range implements Updatable {

  private static final float DEFAULT_LERP_FRACTION = 0.95f;
  private static final float DEFAULT_LERP_THRESHOLD = 0.001f;

  private double myTargetMin;

  private double myTargetMax;

  private float myLerpFraction;

  private float myLerpThreshold;

  public AnimatedRange(double min, double max) {
    super(min, max);
    myTargetMin = min;
    myTargetMax = max;
    myLerpFraction = DEFAULT_LERP_FRACTION;
    myLerpThreshold = DEFAULT_LERP_THRESHOLD;
  }

  public AnimatedRange() {
    this(0, 0);
  }

  @Override
  public void setLerpFraction(float fraction) {
    myLerpFraction = fraction;
  }

  @Override
  public void setLerpThreshold(float threshold) {
    myLerpThreshold = threshold;
  }

  @Override
  public void setMin(double min) {
    myTargetMin = min;
  }

  @Override
  public void setMax(double max) {
    myTargetMax = max;
  }

  @Override
  public void update(float elapsed) {
    if (myMin != myTargetMin) {
      myMin = Choreographer.lerp(myMin, myTargetMin, myLerpFraction, elapsed, myLerpThreshold);
    }

    if (myMax != myTargetMax) {
      myMax = Choreographer.lerp(myMax, myTargetMax, myLerpFraction, elapsed, myLerpThreshold);
    }
  }
}