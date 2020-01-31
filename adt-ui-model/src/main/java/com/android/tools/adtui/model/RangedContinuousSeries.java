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
 * This class adds a name and an additional range to RangedSeries. This additional range represents
 * the Y axis to the default range which represents the x axis.
 */
public class RangedContinuousSeries extends RangedSeries<Long> {

  @NotNull
  private final String myName;

  @NotNull
  private final Range myYRange;

  /**
   * Creates a RangedContinuousSeries with the {@link DataSeries} object scoped by the default and intersecting {@link Range} objects.
   */
  public RangedContinuousSeries(@NotNull String name, @NotNull Range xRange, @NotNull Range yRange, @NotNull DataSeries<Long> series,
                                @NotNull Range intersectRange) {
    super(xRange, series, intersectRange);
    myYRange = yRange;
    myName = name;
  }

  /**
   * Creates a new RangedSeries with the {@link DataSeries} object scoped only by the xRange {@link Range}.
   */
  public RangedContinuousSeries(@NotNull String name, @NotNull Range xRange, @NotNull Range yRange, @NotNull DataSeries<Long> series) {
    this(name, xRange, yRange, series, new Range(-Double.MAX_VALUE, Double.MAX_VALUE));
  }

  @NotNull
  public Range getYRange() {
    return myYRange;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
