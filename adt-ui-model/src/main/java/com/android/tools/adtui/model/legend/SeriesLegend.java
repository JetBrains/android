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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Render data used for displaying LineChart data. In particular, it will show the most recent data in the RangedContinuousSeries
 * based on the max value of the input range.
 */
public final class SeriesLegend implements Legend {

  @NotNull private final Range myRange;
  @NotNull private final RangedContinuousSeries mySeries;
  @NotNull private final BaseAxisFormatter myFormatter;
  @NotNull private final String myName;

  public SeriesLegend(@NotNull RangedContinuousSeries series, @NotNull BaseAxisFormatter formatter, @NotNull Range range) {
    this(series, formatter, range, series.getName());
  }

  public SeriesLegend(@NotNull RangedContinuousSeries series,
                      @NotNull BaseAxisFormatter formatter,
                      @NotNull Range range,
                      @NotNull String name) {
    myRange = range;
    mySeries = series;
    myFormatter = formatter;
    myName = name;
  }

  @Nullable
  @Override
  public String getValue() {
    double time = myRange.getMax();
    ImmutableList<SeriesData<Long>> data = mySeries.getDataSeries().getDataForXRange(new Range(time, time));
    if (data.isEmpty()) {
      return null;
    }

    SeriesData<Long> key = new SeriesData<>(TimeUnit.MICROSECONDS.toNanos((long)time), 0L);
    int index = Collections.binarySearch(data, key, (left, right) -> {
      long diff = left.x - right.x;
      return (diff == 0) ? 0 : (diff < 0) ? -1 : 1;
    });
    index = index >= 0 ? index : -(index + 1); // This returns the data to the right given no exact match.
    index = Math.max(0, Math.min(data.size() - 1, index));

    return myFormatter.getFormattedString(mySeries.getYRange().getLength(), data.get(index).value, true);
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }
}
