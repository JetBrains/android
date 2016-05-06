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

/**
 * An auxiliary object that formats the axis by determining the marker placement positions and their
 * corresponding labels.
 * TODO: move this class and its subclasses to a submodule
 */
public abstract class BaseAxisDomain {

  protected int mMultiplier;

  private final int mMaxMinorTicks;

  private final int mMaxMajorTicks;

  private final int mSwitchThreshold;

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
   */
  protected BaseAxisDomain(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    mMaxMinorTicks = Math.max(1, maxMinorTicks);
    mMaxMajorTicks = Math.max(1, maxMajorTicks);
    mSwitchThreshold = switchThreshold;
  }

  /**
   * @param globalRange The global range of the axis.
   * @param value       The value to display.
   * @return A nicely formatted string to display as the tick label.
   */
  @NonNull
  public String getFormattedString(double globalRange, double value) {
    int index = getMultiplierIndex(globalRange, 1);
    String unit = getUnit(index);
    return String.format("%.2f%s", (float)value / mMultiplier, unit);
  }

  /**
   * Determines the major interval value that should be used for a particular range.
   *
   * @param range The range to calculate intervals for.
   */
  public int getMajorInterval(double range) {
    int index = getMultiplierIndex(range, mSwitchThreshold);
    int base = getUnitBase(index);
    int minInterval = getUnitMinimalInterval(index);
    TIntArrayList factors = getUnitBaseFactors(index);
    return getInterval(range / mMultiplier, mMaxMajorTicks, base, minInterval, factors)
           * mMultiplier;
  }

  /**
   * Determines the minor interval value that should be used for a particular range.
   *
   * @param range The range to calculate intervals for.
   */
  public int getMinorInterval(double range) {
    int index = getMultiplierIndex(range, mSwitchThreshold);
    int base = getUnitBase(index);
    int minInterval = getUnitMinimalInterval(index);
    TIntArrayList factors = getUnitBaseFactors(index);
    return getInterval(range / mMultiplier, mMaxMinorTicks, base, minInterval, factors)
           * mMultiplier;
  }

  /**
   * @return The number of units/scales that the axis uses.
   */
  protected abstract int getNumUnits();

  /**
   * @return The string representation of a unit.
   * @throws IndexOutOfBoundsException If index is out of the expected range.
   */
  @NonNull
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
  @NonNull
  protected abstract TIntArrayList getUnitBaseFactors(int index);

  /**
   * @return Given a value, returns the index of the unit that should be used.
   */
  protected final int getMultiplierIndex(double value, int threshold) {
    mMultiplier = 1;
    int count = getNumUnits();
    for (int i = 0; i < count; i++) {
      int temp = mMultiplier * getUnitMultiplier(i);
      if (value <= temp * threshold) {
        return i;
      }
      mMultiplier = temp;
    }

    return count - 1;
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
                                   int minInterval, TIntArrayList baseFactors) {
    // Find the target interval based on the max num ticks we can render within the range.
    double interval = Math.max(minInterval, range / maxTicks);

    // Order of magnitude of minInterval relative to base.
    int power = (int)Math.floor(Math.log(interval) / Math.log(base));
    int magnitude = (int)Math.pow(base, power);

    // Multiplier of targetInterval at the current magnitude
    // rounded up to at least 1, which is the smallest possible value in the baseFactors array.
    int multiplier = (int)(interval / magnitude + 0.5);

    // Find the closest base factor bigger than multiplier and use that as the multiplier.
    // The idea behind using the factor is that it will add up nicely in the base system,
    // that way we always get integral intervals.
    if (multiplier > 1) {
      for (int i = 1; i < baseFactors.size(); i++) {
        if (multiplier > baseFactors.get(i)) {
          multiplier = baseFactors.get(i - 1);
          break;
        }
      }
    }

    return multiplier * magnitude;
  }

  /**
   * Creates a factor array for the value base. Note that this does not include all factors of
   * base, but it recursively finds the biggest factor that can divide the previous value in the
   * array.
   * e.g. for a base of 10, the result would be {10, 5, 1}
   * e.g. for a base of 60, the result would be {60, 30, 15, 5, 1}
   */
  @NonNull
  protected static TIntArrayList getMultiplierFactors(int base) {
    TIntArrayList factors = new TIntArrayList();
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
      base = base / divider;
    }
    factors.add(1); // Smallest possible factor of base.

    return factors;
  }
}
