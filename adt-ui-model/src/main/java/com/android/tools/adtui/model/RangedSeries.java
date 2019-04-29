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

import java.util.List;

/**
 * This class is the default implementation of a ranged series. It provides access to the DataSeries
 * scoped by a given Range or the intersection of two given ranges.
 *
 * @param <E> This should be the type of data this RangedSeries represents.
 */
public class RangedSeries<E> {

  @NotNull
  protected final Range myRange;

  @NotNull
  protected DataSeries<E> mySeries;

  @NotNull
  protected final Range myIntersectRange;

  /**
   * Creates a new RangedSeries with the {@link DataSeries} object scoped by view and data {@link Range} objects. getSeries will return
   * a series that is the intersection of our view and data ranges.
   *
   * @param viewRange The range of the view.
   * @param series    Store for the data we will provide scoped access to.
   * @param dataRange The range of the data.
   */
  public RangedSeries(@NotNull Range viewRange, @NotNull DataSeries<E> series, @NotNull Range dataRange) {
    myRange = viewRange;
    mySeries = series;
    myIntersectRange = dataRange;
  }

  /**
   * Creates a new RangedSeries with the {@link DataSeries} object scoped only by the provided {@link Range}.
   *
   * @param defaultRange The provided Range.
   * @param series       Store for the data we will provide scoped access to.
   */
  public RangedSeries(@NotNull Range defaultRange, @NotNull DataSeries<E> series) {
    this(defaultRange, series, new Range(-Double.MAX_VALUE, Double.MAX_VALUE));
  }

  /**
   * @return A new, immutable {@link SeriesDataList} consisting of items in the DataStore scoped to the range(s) that the RangedSeries was
   * initialized with.
   */
  @NotNull
  public List<SeriesData<E>> getSeries() {
    return getSeriesForRange(myRange.getIntersection(myIntersectRange));
  }

  /**
   * @param range The range to which the data will be scoped.
   * @return A new, immutable {@link SeriesDataList} that allows the caller to get items in the DataStore scoped to the given range.
   */
  @NotNull
  public List<SeriesData<E>> getSeriesForRange(Range range) {
    return mySeries.getDataForXRange(range);
  }

  /**
   * @return A new range object that represents the intersection between the default and intersect ranges.
   */
  public Range getIntersection() {
    return myRange.getIntersection(myIntersectRange);
  }

  /**
   * @return The {@link Range} object that represents the xRange of this series.
   */
  @NotNull
  public Range getXRange() {
    return myRange;
  }
}
