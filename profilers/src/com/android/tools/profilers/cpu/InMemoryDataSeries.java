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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementers of this class should implement {@link #inMemoryDataList}, which should return all the {@link DataSeries} that would be
 * returned if {@link #getDataForXRange(Range)} receives a range with maximum length.
 */
abstract class InMemoryDataSeries<T> implements DataSeries<T> {
  @NotNull
  protected final CpuProfilerStage myStage;

  public InMemoryDataSeries(@NotNull CpuProfilerStage stage) {
    myStage = stage;
  }

  @Override
  public List<SeriesData<T>> getDataForXRange(Range xRange) {
    long min = (long)xRange.getMin();
    long max = (long)xRange.getMax();
    List<SeriesData<T>> series = new ArrayList<>();
    List<SeriesData<T>> seriesDataList = inMemoryDataList();
    if (seriesDataList.isEmpty()) {
      return series;
    }
    for (int i = 0; i < seriesDataList.size() - 1; i++) {
      SeriesData<T> data = seriesDataList.get(i);
      SeriesData<T> nextData = seriesDataList.get(i + 1);
      // If our series overlaps with the start of the range upto excluding the end. We add the series.
      if (data.x >= max) {
        break;
      }
      // If our next series is greater than our min then we add our current element to the return set. This works because
      // we want to add the element just before our range starts so checking the next element gives us that.
      // After that point all elements will be greater than our min until our current element is > than our max in which case
      // we break out of the loop.
      if (nextData.x > min) {
        series.add(data);
      }
    }
    SeriesData<T> lastElement = seriesDataList.get(seriesDataList.size() - 1);
    // Always add the last element if it is less than the max.
    if (lastElement.x < max) {
      series.add(lastElement);
    }
    return series;
  }

  /**
   * Returns all the {@link SeriesData} stored in memory, to be filtered by range in {@link #getDataForXRange(Range)}
   */
  protected abstract List<SeriesData<T>> inMemoryDataList();
}
