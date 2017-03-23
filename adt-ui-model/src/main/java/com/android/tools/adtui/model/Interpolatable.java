/*
 * Copyright (C) 2017 The Android Open Source Project
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

public interface Interpolatable<T, R> {

  R interpolate(@NotNull SeriesData<T> start, SeriesData<T> end, double x);

  /**
   * If we draw a segment between points (start.x, start.value) and (end.x, end.value) then
   * this interpolatable returns |value|, such that the segment contains (x, |value|) as a point.
   */
  Interpolatable<Long, Double> SegmentInterpolator = ((start, end, x) -> {
    double x1 = start.x, y1 = start.value;
    double x2 = end.x, y2 = end.value;
    return (x - x1) / (x2 - x1) * (y2 - y1) + y1;
  });

  /**
   * Similar to {@link #SegmentInterpolator}, but the value is rounded to the nearest integer.
   */
  Interpolatable<Long, Double> RoundedSegmentInterpolator = ((start, end, x) ->
    (double)Math.round(SegmentInterpolator.interpolate(start, end, x))
  );

  /**
   * SteppedLineInterpolator is used when values of data is always integer.
   * Thus, values of all points in [start.x..end.x) is start.value.
   */
  Interpolatable<Long, Double> SteppedLineInterpolator = ((start, end, x) -> {
    double eps = 1e-6;
    return Math.abs(x - end.x) < eps ? end.value.doubleValue() : start.value.doubleValue();
  });
}
