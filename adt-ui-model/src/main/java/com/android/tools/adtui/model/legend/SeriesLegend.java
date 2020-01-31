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
package com.android.tools.adtui.model.legend;

import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Render data used for displaying LineChart data. In particular, it will show the most recent data in the RangedContinuousSeries
 * based on the max value of the input range.
 */
public final class SeriesLegend implements Legend {

  @VisibleForTesting final static String UNAVAILABLE_MESSAGE = "N/A";

  @NotNull private final Range myRange;
  @NotNull private final RangedContinuousSeries mySeries;
  @NotNull private final BaseAxisFormatter myFormatter;
  @NotNull private final String myName;
  @NotNull private final Interpolatable<Long, Double> myInterpolator;
  @NotNull private final Predicate<Range> myDisplayFilter;

  public SeriesLegend(@NotNull RangedContinuousSeries series, @NotNull BaseAxisFormatter formatter, @NotNull Range range) {
    this(series, formatter, range, Interpolatable.SegmentInterpolator);
  }

  public SeriesLegend(@NotNull RangedContinuousSeries series,
                      @NotNull BaseAxisFormatter formatter,
                      @NotNull Range range,
                      @NotNull Interpolatable<Long, Double> interpolator) {
    this(series, formatter, range, series.getName(), interpolator);
  }

  public SeriesLegend(@NotNull RangedContinuousSeries series,
                      @NotNull BaseAxisFormatter formatter,
                      @NotNull Range range,
                      @NotNull String name,
                      @NotNull Interpolatable<Long, Double> interpolator) {
    this(series, formatter, range, name, interpolator, val -> true);
  }

  /**
   * @param series            the series containing the data source.
   * @param formatter         the formatter to use for rendering the series data
   * @param range             the range to query data from
   * @param name              the title/name of the legend
   * @param interpolator      the interpolate to use for calculating values between data points
   * @param displayFilter     determines whether the legend should be display at a particular range
   */
  public SeriesLegend(@NotNull RangedContinuousSeries series,
                      @NotNull BaseAxisFormatter formatter,
                      @NotNull Range range,
                      @NotNull String name,
                      @NotNull Interpolatable<Long, Double> interpolator,
                      @NotNull Predicate<Range> displayFilter) {
    myRange = range;
    mySeries = series;
    myFormatter = formatter;
    myName = name;
    myInterpolator = interpolator;
    myDisplayFilter = displayFilter;
  }

  @Nullable
  @Override
  public String getValue() {
    double time = myRange.getMax();
    Range range = new Range(time, time);
    if (!myDisplayFilter.test(range)) {
      return UNAVAILABLE_MESSAGE;
    }

    List<SeriesData<Long>> data = mySeries.getSeriesForRange(range);
    if (data.isEmpty()) {
      // SeriesLegend should always show up, even when data is absent.
      return UNAVAILABLE_MESSAGE;
    }
    return myFormatter.getFormattedString(mySeries.getYRange().getLength(), getInterpolatedValueAt(time, data), true);
  }

  private double getInterpolatedValueAt(double time, @NotNull List<SeriesData<Long>> data) {
    SeriesData<Long> key = new SeriesData<>((long)time, 0L);
    int index = Collections.binarySearch(data, key, (left, right) -> {
      long diff = left.x - right.x;
      return (diff == 0) ? 0 : (diff < 0) ? -1 : 1;
    });
    if (index >= 0) {
      return data.get(index).value;
    }
    // This returns the data to the right given no exact match.
    index = -(index + 1);
    if (index == 0) {
      return data.get(index).value;
    }
    if (index >= data.size()) {
      return data.get(data.size() - 1).value;
    }
    return myInterpolator.interpolate(data.get(index - 1), data.get(index), time);
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }
}
