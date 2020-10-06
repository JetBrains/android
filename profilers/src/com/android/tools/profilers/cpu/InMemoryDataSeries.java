/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Implementers of this class should implement {@link #inMemoryDataList}, which should return all the {@link DataSeries} that would be
 * returned if {@link #getDataForRange(Range)} receives a range with maximum length.
 */
abstract class InMemoryDataSeries<T> implements DataSeries<T> {
  /**
   * @return list of {@link SeriesData} within the given range, plus the data points before/after unless they match exactly the boundaries
   * of the given range.
   */
  @Override
  public List<SeriesData<T>> getDataForRange(Range range) {
    List<SeriesData<T>> result = new ArrayList<>();
    List<SeriesData<T>> seriesDataList = inMemoryDataList();
    if (seriesDataList.isEmpty() || range.isEmpty()) {
      return result;
    }
    // Create fake SeriesData for min and max as search keys.
    SeriesData<Object> minSearchKey = new SeriesData<>((long)range.getMin(), null);
    SeriesData<Object> maxSearchKey = new SeriesData<>((long)range.getMax(), null);
    int minIndex = Collections.binarySearch(seriesDataList, minSearchKey, Comparator.comparingLong(o -> o.x));
    int maxIndex = Collections.binarySearch(seriesDataList, maxSearchKey, Comparator.comparingLong(o -> o.x));
    // When the search key is not found, binarySearch returns (-insertion_point - 1), where insertion_point is the index at which the key
    // would be inserted into the list.
    if (minIndex < 0) {
      // Range.min not found. The insertion_point is (-minIndex - 1). We want to include the point before it, i.e. (insertion_point - 1),
      // unless it is already the first point.
      minIndex = Math.max(-minIndex - 2, 0);
    }
    if (maxIndex < 0) {
      // Range.max not found. The insertion_point is (-maxIndex - 1). We want to include the point after it, i.e. insertion_point, unless it
      // is already the last point.
      maxIndex = Math.min(-maxIndex - 1, seriesDataList.size() - 1);
    }
    // Return all data points from minIndex to maxIndex, both inclusive.
    result.addAll(seriesDataList.subList(minIndex, maxIndex + 1));
    return result;
  }

  /**
   * @return all the {@link SeriesData} stored in memory (sorted by {@link SeriesData#x}, to be filtered by range in
   * {@link #getDataForRange(Range)}. Note that for best performance it is recommended to returning a {@link java.util.RandomAccess} list.
   */
  @NotNull
  protected abstract List<SeriesData<T>> inMemoryDataList();
}
