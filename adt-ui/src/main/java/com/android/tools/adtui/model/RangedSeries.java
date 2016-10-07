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

import com.android.tools.adtui.Range;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * This class is the default implementation of a ranged series. It provides access to the DataSeries,
 * and the xRange that all UI components use.
 * @param <E> This should be the type of data this RangedSeries represents.
 */
public class RangedSeries<E> {
  @NotNull
  protected final Range mXRange;

  @NotNull
  protected final DataSeries<E> mSeries;

  /**
   * When constructing a RangedSeries the caller needs to supply a {@link Range} object that manages the scope of the data, and
   * a {@link DataSeries} object, that manages access to the raw data.
   * @param xRange
   * @param series
   */
  public RangedSeries(Range xRange, DataSeries<E> series) {
    mXRange = xRange;
    mSeries = series;
  }

  /**
   * @return A new {@link SeriesDataList} that is immutable. This allows the caller to get a scoped enumeration of items in the DataStore.
   */
  @NotNull
  public ImmutableList<SeriesData<E>> getSeries() {
    return mSeries.getDataForXRange(mXRange);
  }

  /**
   * @return The {@link Range} object that represents the xRange of this series.
   */
  @NotNull
  public Range getXRange() {
    return mXRange;
  }
}
