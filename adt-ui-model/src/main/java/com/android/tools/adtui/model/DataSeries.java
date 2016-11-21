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

import com.intellij.util.containers.ImmutableList;

/**
 * An interface that provides data to all RangedSeries used by the UI.
 */
public interface DataSeries<E> {

  ImmutableList<SeriesData<E>> getDataForXRange(Range xRange);

  /**
   * @return the SeriesData at x. If no exact match is available, this returns the data to the immediate right of x.
   *         If there is no data to the right, the data to the immediate left is returned.
   */
  default SeriesData<E> getClosestData(long x) {
    return null;
  }

  /**
   * In binary search, a negative index indicates the desire data point is not present, and the negative result
   * is computed by -(insertion_point + 1). This helper function converts the negative result back to our desired
   * index value and clamps it to the size of the data set.
   */
  static int convertBinarySearchIndex(int index, int size) {
    index = index >= 0 ? index : -(index + 1); // This returns the data to the right given no exact match.
    return Math.max(0, Math.min(size - 1, index));
  }
}
