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
package com.android.tools.idea.monitor.datastore;

import com.android.tools.adtui.model.SeriesData;
import gnu.trove.TLongArrayList;

/**
 * Interface to use with the {@link SeriesDataStore}. This object represents the in memory representation of the data available.
 * @param <T> The type of data to be returned when requested from the {@link SeriesDataStore}
 */
public interface DataAdapter<T> {

  /**
   * This function should return the index for a specific time. The time passed in here will be the delta time
   * between the start time and the time requested. For example if the UI requested the first point in time it would pass 0.
   *
   * TODO: think about a refactoring that allows this method to be reused across classes that implement this interface.
   * All the implementations look like the same now.
   *
   * @param leftClosest if there is no exact match and true, return the closest left index. Otherwise, return the closest right index.
   */
  int getClosestTimeIndex(long timeUs, boolean leftClosest);

  /**
   * Each data adapter is responsible for creating a {@link SeriesData} object that will be returned to the UI in use for rendering.
   */
  SeriesData<T> get(int index);

  /**
   * Clears any previous data and resets any new/incoming data to be relative to the new startTime.
   * @param deviceStartTimeUs the data starting point in device time.
   * @param studioStartTimeUs the data starting point in studio time.
   *                          TODO this is currently used for test data generators to convert test data timestamps back to device time.
   */
  void reset(long deviceStartTimeUs, long studioStartTimeUs);

  /**
   * Stops any ongoing data polls.
   */
  void stop();

  /**
   * See {@link #getClosestTimeIndex(long, boolean)} for details.
   */
  static int getClosestIndex(TLongArrayList list, long value, boolean leftClosest) {
    int index = list.binarySearch(value);
    index = convertBinarySearchIndex(index, leftClosest);

    return Math.max(0, Math.min(list.size(), index));
  }

  /**
   * In binary search, a negative index indicates that the desire data point is not present, and the negative result
   * is computed by -(insertion_point + 1). This helper function converts the negative result back to our desired
   * index value.
   *
   * @param left if true, returns the index left to the missing data point. Otherwise, return the right index.
   *
   */
  static int convertBinarySearchIndex(int index, boolean left) {
    return index >= 0 ? index : -index - (left ? 2 : 1);
  }
}
