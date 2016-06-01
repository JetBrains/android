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

import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.Range;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents a view into a continuous series, where the data in view is only
 * within given x and y ranged.
 */
public class RangedContinuousSeries implements ReportingSeries{
  @NotNull
  private final Range mXRange;

  @NotNull
  private final Range mYRange;

  @NotNull
  private final String mLabel;

  @NotNull
  private final ContinuousSeries mSeries;

  private BaseAxisFormatter mXAxisFormatter;

  private BaseAxisFormatter mYAxisFormatter;

  public RangedContinuousSeries(@NotNull String label, @NotNull Range xRange, @NotNull Range yRange, @NotNull ContinuousSeries series) {
    mLabel = label;
    mXRange = xRange;
    mYRange = yRange;
    mSeries = series;
  }

  public RangedContinuousSeries(@NotNull String label, @NotNull Range xRange, @NotNull Range yRange, @NotNull ContinuousSeries series,
                                @NotNull BaseAxisFormatter xAxisFormatter, @NotNull BaseAxisFormatter yAxisFormatter) {
    this(label, xRange, yRange, series);
    mXAxisFormatter = xAxisFormatter;
    mYAxisFormatter = yAxisFormatter;
  }

  /**
   * @return A new {@link SeriesDataList} that is immutable. This allows the caller to get a scoped enumeration of items in the DataStore.
   */
  @NotNull
  public ImmutableList<SeriesData<Long>> getSeries() {
    return mSeries.getDataForXRange(mXRange);
  }

  @NotNull
  public Range getYRange() {
    return mYRange;
  }

  @NotNull
  public Range getXRange() {
    return mXRange;
  }

  /**
   * Helper function for getting the maximum time and data value for a given series. This is used by the reporting data to determine how
   * values should be reported back to the UI.
   */
  private static SeriesData<Long> getMaxYValue(ImmutableList<SeriesData<Long>> series) {
    SeriesData<Long> maxData = series.get(0);
    for(int i = 1; i < series.size(); i++) {
      SeriesData<Long> data = series.get(i);
      if(maxData.x < data.x) {
        maxData.x = data.x;
      }
      if(maxData.value < data.value) {
        maxData.value = data.value;
      }
    }
    return maxData;
  }

  @Nullable
  private ReportingData getReportingData(long time) {
    ImmutableList<SeriesData<Long>> series = getSeries();
    if(series.size() == 0) {
      return null;
    }

    SeriesData data = mSeries.getDataAtXValue(time);
    SeriesData maxData = getMaxYValue(series);
    long nearestX = data.x;
    long nearestY = (long)data.value;
    long maxX = maxData.x;
    long maxY = (long)maxData.value;

    String formattedY = mYAxisFormatter == null ? Long.toString(nearestY) : mYAxisFormatter.getFormattedString(maxY, nearestY);
    String formattedX = mXAxisFormatter == null ? Long.toString(nearestX) : mXAxisFormatter.getFormattedString(maxX, nearestX);
    return new ReportingData(nearestX, mLabel, formattedX, formattedY);
  }

  @Override
  @Nullable
  public ReportingData getLatestReportingData() {
    //TODO change this to get global latest per design. Currently this returns local latest eg, if zoomed in.
    return getReportingData((long)mXRange.getMax());
  }

  @Override
  public Collection<ReportingData> getFullReportingData(long x) {
    ArrayList<ReportingData> dataList = new ArrayList<>();
    ReportingData report = getReportingData(x);
    if(report != null) {
      dataList.add(report);
    }
    return dataList;
  }
}
