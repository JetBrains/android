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
package com.android.tools.datastore;

import com.android.tools.adtui.model.SeriesData;
import gnu.trove.TLongArrayList;

/**
 * Interface to use with the {@link SeriesDataStore}. This object represents the in memory representation of the data available.
 * @param <T> The type of data to be returned when requested from the {@link SeriesDataStore}
 */
public interface DataAdapter<T> {

  int SAMPLE_INDEX_BUFFER = 1;

  /**
   * This function should return the index for a specific time (device time microseconds).
   *
   * @param leftClosest if there is no exact match and true, return the closest left index. Otherwise, return the closest right index.
   */
  int getClosestTimeIndex(long timeUs, boolean leftClosest);

  /**
   * Each data adapter is responsible for creating a {@link SeriesData} object that will be returned to the UI in use for rendering.
   */
  SeriesData<T> get(int index);

  /**
   * Clears any previous data.
   */
  void reset();

  /**
   * Stops any ongoing data polls.
   */
  void stop();

  /**
   * See {@link #getClosestTimeIndex(long, boolean)} for details.
   */
  public static int getClosestIndex(TLongArrayList list, long value, boolean leftClosest) {
    int index = list.binarySearch(value);
    return convertBinarySearchIndex(index, list.size(), leftClosest);
  }

  /**
   * In binary search, a negative index indicates the desire data point is not present, and the negative result
   * is computed by -(insertion_point + 1). This helper function converts the negative result back to our desired
   * index value. Then, it shifts the index by {@link #SAMPLE_INDEX_BUFFER} to the left/right before returning the result.
   * The extra buffer allows us to gather information on samples that are immediately outside of our region of interest.
   * e.g. LineChart series need to collect samples offscreen to complete the lines.
   *
   * Let numbers denote sample indices and || represents our viewing time range below.
   *
   *       index buffer                                                   index buffer
   * A  <-----------------> B    |                              |   C  <------------------> D
   * 0                      1    |      2        3         4    |   5                       6
   * ===========================================================================================
   *
   * Binary search would give us indices at B and C after adjusting for negative results. The additional
   * index buffer would give us A and D as the results.
   *
   * @param left if true, returns the index left to the missing data point. Otherwise, return the right index.
   */
  static int convertBinarySearchIndex(int index, int size, boolean left) {
    index = index >= 0 ? index : -index - (left ? 2 : 1);
    index += left ? -SAMPLE_INDEX_BUFFER : SAMPLE_INDEX_BUFFER;
    return Math.max(0, Math.min(size, index));
  }
}
