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

import org.jetbrains.annotations.NotNull;

/**
 * Represents a view into a continuous series, where the data in view is only
 * within given x and y ranged.
 */
public class RangedContinuousSeries extends RangedSeries<Long> {

  @NotNull
  private final String myName;

  @NotNull
  private final Range mYRange;

  public RangedContinuousSeries(@NotNull String name, @NotNull Range xRange, @NotNull Range yRange, @NotNull DataSeries<Long> series) {
    super(xRange, series);
    mYRange = yRange;
    myName = name;
  }

  @NotNull
  public Range getYRange() {
    return mYRange;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
