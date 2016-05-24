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

import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * A data series representing changes in an Enum state over time.
 * The series is currently designed to record state changes as follows:
 *
 * If the states at time 0...4 is [a a b b b], the data will be stored as
 * [0:a, 2:b]
 *
 * This model may change in the future if we need to record start/end or overlapping events differently,
 * but the current implementation can be adapted for those behaviors as well. For example, start/end
 * events can be treated as ON versus OFF states, while overlapping events can be stored as
 * multiple DiscreteSeries.
 */
public class DiscreteSeries<E extends Enum<E>> {

  private int mLastValue = -1;

  @NotNull
  private final TLongArrayList mX = new TLongArrayList();

  @NotNull
  private final TIntArrayList mY = new TIntArrayList();

  @NotNull
  private final E[] mEnumValues;

  public DiscreteSeries(@NotNull Class<E> enumClass) {
    mEnumValues = enumClass.getEnumConstants();
  }

  /**
   * Add a data point to the series.
   * If the same value y is repeated over a range of x, only the first occurrence will be stored.
   *
   * Assumption - {@code x} should be greater than all existing x values in the data.
   */
  public void add(long x, E y) {
    int value = y.ordinal();
    if (value != mLastValue) {
      mX.add(x);
      mY.add(value);
      mLastValue = value;
    }
  }

  public int size() {
    return mX.size();
  }

  public long getX(int index) {
    return mX.get(index);
  }

  @NotNull
  public E getValue(int index) {
    return mEnumValues[mY.get(index)];
  }

  /**
   * Given {@code xValue}, find the Enum state Y that corresponds to the same data point.
   * If {@code xValue} does not match any existing data point, the closest x to the left of {@code xValue}
   * in the dataset is used, as that would be the most recent known data from {@code xValue}.
   *
   * @param xValue the xValue value to search for
   * @return The corresponding state y from the same data point as xValue.
   * If there is no data &le; xValue, the default enum state is returned.
   */
  public E findYFromX(long xValue) {
    int index = mX.binarySearch(xValue);

    if (index < 0) {
      // No exact match, returns position to the left of the insertion point.
      // NOTE: binarySearch returns -(insertion point + 1) if not found.
      index = -index - 2;
    }

    return index >= 0 ? getValue(index) : mEnumValues[0];
  }
}
