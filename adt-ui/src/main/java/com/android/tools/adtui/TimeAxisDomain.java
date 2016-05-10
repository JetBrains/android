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
package com.android.tools.adtui;

import com.android.annotations.NonNull;
import gnu.trove.TIntArrayList;

public final class TimeAxisDomain extends BaseAxisDomain {

  private static final int[] MULTIPLIERS = new int[]{1000, 60, 60, 24};   // 1s, 1m, 1h, 1d
  private static final int[] BASES = new int[]{10, 60, 60, 24};
  private static final int[] MIN_INTERVALS = new int[]{10, 1, 1, 1};    // 10ms, 1s, 1m, 1h
  private static final String[] UNITS = new String[]{"ms", "s", "m", "h"};
  private static final TIntArrayList[] BASE_FACTORS;

  public static final TimeAxisDomain DEFAULT = new TimeAxisDomain(5, 5, 5);

  static {
    int size = BASES.length;
    BASE_FACTORS = new TIntArrayList[size];
    for (int i = 0; i < size; i++) {
      BASE_FACTORS[i] = getMultiplierFactors(BASES[i]);
    }
  }

  public TimeAxisDomain(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  /**
   * The base class implementation only shows a single unit at the current scale.
   * Time formatting behaves differently as we want to show 1m30s instead
   * of 1.5m, or 1h15m instead of 1.25h, for example. So here the implementation is
   * extended to include the extra unit/scale when appropriate.
   */
  @Override
  @NonNull
  public String getFormattedString(double globalRange, double value) {
    int index1 = getMultiplierIndex(globalRange, 1);
    int scale1 = mMultiplier;
    String unit1 = getUnit(index1);
    if (index1 > 1) {
      int index2 = index1 - 1;
      int scale2 = scale1 / getUnitMultiplier(index2);
      String unit2 = getUnit(index2);
      int value1 = (int)(value / scale1);
      float value2 = (float)(value - value1 * scale1) / scale2;

      if (value2 > 0) {
        if (value1 > 0) {
          return String.format("%d%s%.2f%s", value1, unit1, value2, unit2);
        }
        else {
          return String.format("%.2f%s", value2, unit2);
        }
      }
      else {
        return String.format("%d%s", value1, unit1);
      }
    }
    else {
      return String.format("%.2f%s", value / scale1, unit1);
    }
  }

  @Override
  protected int getNumUnits() {
    return UNITS.length;
  }

  @Override
  @NonNull
  protected String getUnit(int index) {
    return UNITS[index];
  }

  @Override
  protected int getUnitBase(int index) {
    return BASES[index];
  }

  @Override
  protected int getUnitMultiplier(int index) {
    return MULTIPLIERS[index];
  }

  @Override
  protected int getUnitMinimalInterval(int index) {
    return MIN_INTERVALS[index];
  }

  @Override
  @NonNull
  protected TIntArrayList getUnitBaseFactors(int index) {
    return BASE_FACTORS[index];
  }
}
