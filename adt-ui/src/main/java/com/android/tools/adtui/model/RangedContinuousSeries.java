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

import com.android.annotations.NonNull;
import com.android.tools.adtui.BaseAxisDomain;
import com.android.tools.adtui.Range;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents a view into a continuous series, where the data in view is only
 * within given x and y ranged.
 */
public class RangedContinuousSeries implements ReportingSeries {
  @NonNull
  private final Range mXRange;

  @NonNull
  private final Range mYRange;

  @NonNull
  private final ContinuousSeries mSeries;

  private BaseAxisDomain mXDomain;

  private BaseAxisDomain mYDomain;

  public RangedContinuousSeries(@NonNull Range xRange, @NonNull Range yRange) {
    mXRange = xRange;
    mYRange = yRange;
    mSeries = new ContinuousSeries();
  }

  public RangedContinuousSeries(@NonNull Range xRange, @NonNull Range yRange,
                                @NonNull BaseAxisDomain xDomain, @NonNull BaseAxisDomain yDomain) {
    this(xRange, yRange);
    mXDomain = xDomain;
    mYDomain = yDomain;
  }

  @NonNull
  public ContinuousSeries getSeries() {
    return mSeries;
  }

  @NonNull
  public Range getYRange() {
    return mYRange;
  }

  @NonNull
  public Range getXRange() {
    return mXRange;
  }

  @Override
  public double getRangeLength() {
    return mYRange.getLength();
  }

  @Override
  public double getLatestValue() {
    double value = 0.0;
    if (mSeries.size() != 0) {
      value = mSeries.getY(mSeries.size() - 1);
    }
    return value;
  }

  @Override
  public Collection<ReportingData> getFullReportingData(long x) {
    ArrayList<ReportingData> dataList = new ArrayList<>();

    int nearestIndex = mSeries.getNearestXIndex(x);
    if (nearestIndex >= 0) {
      long nearestX = mSeries.getX(nearestIndex);
      long nearestY = mSeries.getY(nearestIndex);
      long maxX = mSeries.getMaxX();
      long maxY = mSeries.getMaxY();

      // TODO support named series.
      String formattedY = mYDomain == null ? Long.toString(nearestY) : mYDomain.getFormattedString(maxY, nearestY);
      String formattedX = mXDomain == null ? Long.toString(nearestX) : mXDomain.getFormattedString(maxX, nearestX);
      dataList.add(new ReportingData(nearestX, formattedX, formattedY));
    }

    return dataList;
  }
}
