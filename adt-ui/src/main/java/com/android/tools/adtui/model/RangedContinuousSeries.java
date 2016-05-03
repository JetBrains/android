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

package com.android.tools.adtui.model;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Range;

/**
 * Represents a view into a continuous series, where the data in view is only
 * within given x and y ranged.
 */
public class RangedContinuousSeries {
  @NonNull
  private final Range mXRange;

  @NonNull
  private final Range mYRange;

  @NonNull
  private final ContinuousSeries mSeries;

  public RangedContinuousSeries(@NonNull Range xRange, @NonNull Range yRange) {
    mXRange = xRange;
    mYRange = yRange;
    mSeries = new ContinuousSeries();
  }

  @NonNull
  public ContinuousSeries getSeries() {
    return mSeries;
  }

  @NonNull
  public Range getYRange() {
    return mYRange;
  }

  @NonNull
  public Range getXRange() {
    return mXRange;
  }
}
