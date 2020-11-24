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
import it.unimi.dsi.fastutil.ints.IntList;
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
  private static final IntList[] BASE_FACTORS;

  public static final TimeAxisFormatter DEFAULT = new TimeAxisFormatter(5, 10, 5);
  public static final TimeAxisFormatter DEFAULT_WITHOUT_MINOR_TICKS = new TimeAxisFormatter(1, 10, 5);

  static {
    int size = BASES.length;
    BASE_FACTORS = new IntList[size];
    for (int i = 0; i < size; i++) {
      BASE_FACTORS[i] = getMultiplierFactors(BASES[i]);
    }
  }

  public TimeAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  /**
   * The base class implementation shows a single unit at the current scale.
   * Fot time axis, use TimeFormatter's behavior instead.
   */
  @Override
  @NotNull
  public String getFormattedString(double globalRange, double value, boolean includeUnit) {
    return TimeFormatter.getSimplifiedClockString((long)(value));
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
  protected IntList getUnitBaseFactors(int index) {
    return BASE_FACTORS[index];
  }
}
