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

import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.function.DoubleToIntFunction;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

/**
 * An auxiliary object that formats the axis by determining the marker placement positions and their
 * corresponding labels.
 */
public abstract class BaseAxisFormatter {

  private final int mMaxMinorTicks;

  private final int mMaxMajorTicks;

  private final int mSwitchThreshold;

  private final boolean mHasSeparator;

  private final DoubleToIntFunction mMaxDecimals;

  /**
   * @param maxMinorTicks   The maximum number of minor ticks in a major interval. Note that this
   *                        should be greater than zero.
   * @param maxMajorTicks   The maximum number of major ticks along the whole axis. Note that this
   *                        should be greater than zero.
   * @param switchThreshold A multiplier that indicates the number of minimal units that must
   *                        exist at the current scale before using a larger scale. This avoids
   *                        the problem of very sparse major ticks when the axis first jumps to
   *                        the next scale. e.g. On a time axis with a switchThreshold value of 5,
   *                        the axis will return millisecond intervals up to 5000ms before
   *                        transitioning to second intervals.
   * @param hasSeparator    Whether there is a space separating the value and the unit (e.g. 10% vs 10 MB).
   * @param maxDecimals     Function computing max decimals to display for number
   */
  protected BaseAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold, boolean hasSeparator,
                              DoubleToIntFunction maxDecimals) {
    mMaxMinorTicks = Math.max(1, maxMinorTicks);
    mMaxMajorTicks = Math.max(1, maxMajorTicks);
    mSwitchThreshold = switchThreshold;
    mHasSeparator = hasSeparator;
    mMaxDecimals = maxDecimals;
  }

  protected BaseAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold, boolean hasSeparator) {
    this(maxMinorTicks, maxMajorTicks, switchThreshold, hasSeparator, n -> 1);
  }

  protected BaseAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    this(maxMinorTicks, maxMajorTicks, switchThreshold, false);
  }

  /**
   * @param globalRange The global range of the axis.
   * @param value       The value to display.
   * @param includeUnit Whether to include unit in the display.
   * @return A nicely formatted string to display as the tick label.
   */
  @NotNull
  public String getFormattedString(double globalRange, double value, boolean includeUnit) {
    Multiplier multiplier = getMultiplier(globalRange, 1);
    double multipliedValue = value / multiplier.accumulation;
    // If value is an integer number, don't include the floating point/decimal places in the formatted string.
    // Otherwise, add up to specified decimal places of value.
    DecimalFormat decimalFormat = new DecimalFormat("#." + StringUtil.repeat("#", mMaxDecimals.applyAsInt(multipliedValue)));
    String formattedValue = decimalFormat.format(multipliedValue);
    if (!includeUnit) {
      return formattedValue;
    }
    String pattern = mHasSeparator ? "%s %s" : "%s%s";
    return String.format(pattern, formattedValue, getUnit(multiplier.index)).trim();
  }

  /**
   * Determines the major interval value that should be used for a particular range.
   *
   * @param range The range to calculate intervals for.
   */
  public long getMajorInterval(double range) {
    return getInterval(range, mMaxMajorTicks);
  }

  /**
   * Determines the minor interval value that should be used for a particular range.
   *
   * @param range The range to calculate intervals for.
   */
  public long getMinorInterval(double range) {
    return getInterval(range, mMaxMinorTicks);
  }

  /**
   * Determines the interval value for a particular range given the number of ticks that should be used.
   */
  public long getInterval(double range, int numTicks) {
    Multiplier multiplier = getMultiplier(range, mSwitchThreshold);
    int base = getUnitBase(multiplier.index);
    int minInterval = getUnitMinimalInterval(multiplier.index);
    IntList factors = getUnitBaseFactors(multiplier.index);
    return getInterval(range / multiplier.accumulation, numTicks, base, minInterval, factors)
           * multiplier.accumulation;
  }

  /**
   * @return The number of units/scales that the axis uses.
   */
  protected abstract int getNumUnits();

  /**
   * @return The string representation of a unit.
   * @throws IndexOutOfBoundsException If index is out of the expected range.
   */
  @NotNull
  protected abstract String getUnit(int index);

  /**
   * @return The base value corresponding to a unit.
   * e.g. For time, ms → 10, s → 60, m → 60
   * @throws IndexOutOfBoundsException If index is out of the expected range.
   */
  protected abstract int getUnitBase(int index);

  /**
   * @return The ratio between the current and next units.
   * e.g. For time, ms → 1000, s → 60, m → 60
   * @throws IndexOutOfBoundsException If index is out of the expected range.
   */
  protected abstract int getUnitMultiplier(int index);

  /**
   * @return The minimal interval that should be used for a unit.
   * @throws IndexOutOfBoundsException If index is out of the expected range.
   */
  protected abstract int getUnitMinimalInterval(int index);

  /**
   * @return The list of interval factors that the axis should use for a unit.
   * @throws IndexOutOfBoundsException If index is out of the expected range.
   */
  @NotNull
  protected abstract IntList getUnitBaseFactors(int index);

  /**
   * @return Given a value, returns the index of the unit that should be used.
   */
  protected Multiplier getMultiplier(double value, int threshold) {
    long multiplier = 1;
    int count = getNumUnits();
    for (int i = 0; i < count; i++) {
      long temp = multiplier * getUnitMultiplier(i);
      if (value < temp * threshold) {
        return new Multiplier(i, multiplier);
      }
      multiplier = temp;
    }

    return new Multiplier(count - 1, multiplier);
  }

  /**
   * Determines the interval value that should be used for a particular range. The return value is
   * expected to be some nice factor or multiple of base.
   *
   * @param range       The range to calculate intervals for.
   * @param maxTicks    The maximum number of ticks.
   * @param base        The base system used by the intervals on the axis.
   * @param minInterval The minimal possible interval.
   * @param baseFactors A factor array of base in descending order. see mBaseFactor in
   *                    AxisComponent.
   */
  protected static int getInterval(double range, int maxTicks, int base,
                                   int minInterval, IntList baseFactors) {
    // Find the target interval based on the max num ticks we can render within the range.
    double interval = Math.max(minInterval, range / maxTicks);

    // Order of magnitude of minInterval relative to base.
    int power = (int)Math.floor(Math.log(interval) / Math.log(base));
    int magnitude = (int)Math.pow(base, power);

    // Multiplier of targetInterval at the current magnitude
    // rounded up to at least 1, which is the smallest possible value in the baseFactors array.
    float multiplier = (float)Math.max(1, interval / magnitude);

    // Find the closest base factor bigger than multiplier and use that as the multiplier.
    // The idea behind using the factor is that it will add up nicely in the base system,
    // that way we always get integral intervals.
    if (multiplier > 1) {
      for (int i = 1; i < baseFactors.size(); i++) {
        if (multiplier > baseFactors.getInt(i)) {
          multiplier = baseFactors.getInt(i - 1);
          break;
        }
      }
    }

    return (int)multiplier * magnitude;
  }

  /**
   * Creates a factor array for the value base. Note that this does not include all factors of
   * base, but it recursively finds the biggest factor that can divide the previous value in the
   * array.
   * e.g. for a base of 10, the result would be {10, 5, 1}
   * e.g. for a base of 60, the result would be {60, 30, 15, 5, 1}
   */
  @NotNull
  protected static IntList getMultiplierFactors(int base) {
    IntList factors = new IntArrayList();
    while (base > 1) {
      // Find the smallest factor that can divide base.
      int divider = 2;
      while (base % divider != 0) {
        ++divider;

        // If divider is bigger than the square root of base,
        // then base is prime and smallest factor is base.
        if (divider * divider > base) {
          divider = base;
          break;
        }
      }

      factors.add(base);
      base /= divider;
    }
    factors.add(1); // Smallest possible factor of base.

    return factors;
  }

  protected static class Multiplier {
    final int index;
    final long accumulation;
    Multiplier(int index, long accumulation) {
      this.index = index;
      this.accumulation = accumulation;
    }
  }
}
