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
package com.android.tools.adtui.model.formatter;

import com.google.common.annotations.VisibleForTesting;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * This formatter assumes a microsecond input value.
 */
public final class TimeAxisFormatter extends BaseAxisFormatter {

  private static final int[] MULTIPLIERS = new int[]{1000, 1000, 60, 60, 24};   // 1ms, 1s, 1m, 1h, 1d
  private static final int[] BASES = new int[]{10, 10, 60, 60, 24};
  private static final int[] MIN_INTERVALS = new int[]{10, 10, 1, 1, 1};    // 10ms, 1s, 1m, 1h
  private static final String[] UNITS = new String[]{"us", "ms", "s", "m", "h"};
  private static final TIntArrayList[] BASE_FACTORS;

  public static final TimeAxisFormatter DEFAULT = new TimeAxisFormatter(5, 10, 5);
  public static final TimeAxisFormatter DEFAULT_WITHOUT_MINOR_TICKS = new TimeAxisFormatter(1, 10, 5);

  static {
    int size = BASES.length;
    BASE_FACTORS = new TIntArrayList[size];
    for (int i = 0; i < size; i++) {
      BASE_FACTORS[i] = getMultiplierFactors(BASES[i]);
    }
  }

  public TimeAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  /**
   * The base class implementation only shows a single unit at the current scale.
   * Time formatting behaves differently as we want to show 1m30s instead
   * of 1.5m, or 1h15m instead of 1.25h, for example. So here the implementation is
   * extended to include the extra unit/scale when appropriate.
   */
  @Override
  @NotNull
  public String getFormattedString(double globalRange, double value, boolean includeUnit) {
    if (!includeUnit) {
      return super.getFormattedString(globalRange, value, includeUnit);
    }

    int index1 = getMultiplierIndex(globalRange, 1);
    long scale1 = getMultiplier();
    String unit1 = getUnit(index1);
    if (index1 > 1) {
      int index2 = index1 - 1;
      long scale2 = scale1 / getUnitMultiplier(index2);
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

  @NotNull
  public String getFixedPointFormattedString(long fixedPoint, long value) {
    int baseIndex = getMultiplierIndex(fixedPoint, 1);
    long baseScale = getMultiplier();

    long factor = value / baseScale;
    if (factor < 1) {
      return String.format("0%s", getUnit(baseIndex));
    }

    long truncatedValue = factor * baseScale;
    int leadIndex = baseIndex;
    long leadScale = baseScale;
    for (int i = leadIndex + 1; i < getNumUnits(); i++) {
      long cumulativeScale = leadScale * getUnitMultiplier(leadIndex);
      if (truncatedValue < cumulativeScale) {
        break;
      }
      leadIndex = i;
      leadScale = cumulativeScale;
    }

    StringBuilder builder = new StringBuilder();
    while (leadIndex >= baseIndex && truncatedValue > 0) {
      long truncatedFactor = truncatedValue / leadScale;
      if (truncatedFactor != 0) {
        builder.append(String.format("%d%s", truncatedFactor, getUnit(leadIndex)));
      }
      truncatedValue -= truncatedFactor * leadScale;
      leadScale /= getUnitMultiplier(leadIndex);
      leadIndex--;
    }
    return builder.toString();
  }

  @NotNull
  public String getClockFormattedString(long micro) {
    long milli = TimeUnit.MICROSECONDS.toMillis(micro) % TimeUnit.SECONDS.toMillis(1);
    long sec = TimeUnit.MICROSECONDS.toSeconds(micro) % TimeUnit.MINUTES.toSeconds(1);
    long min = TimeUnit.MICROSECONDS.toMinutes(micro) % TimeUnit.HOURS.toMinutes(1);
    long hour = TimeUnit.MICROSECONDS.toHours(micro);

    return String.format("%02d:%02d:%02d.%03d", hour, min, sec, milli);
  }

  @NotNull
  public String getFormattedDuration(long micros) {
    String[] units = new String[]{"μs", "ms", "s", "m", "h"};

    float[] multipliers = new float[]{
      1,
      TimeUnit.MILLISECONDS.toMicros(1),
      TimeUnit.SECONDS.toMicros(1),
      TimeUnit.MINUTES.toMicros(1),
      TimeUnit.HOURS.toMicros(1)
    };

    assert multipliers.length == units.length;

    long value = micros;
    String unit = units[0];
    for (int i = units.length - 1; i >= 0; --i) {
      if (micros / multipliers[i] >= 1) {
        value = Math.round(micros / multipliers[i]);
        unit = units[i];
        break;
      }
    }
    return String.format("%d %s", value, unit);
  }

  @Override
  protected int getNumUnits() {
    return UNITS.length;
  }

  @Override
  @NotNull
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
  @NotNull
  protected TIntArrayList getUnitBaseFactors(int index) {
    return BASE_FACTORS[index];
  }
}
