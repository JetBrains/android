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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

public final class MemoryAxisFormatter extends BaseAxisFormatter {

  private static final int MULTIPLIER = 1024;
  private static final int BASE = 2;
  private static final int[] MIN_INTERVALS = new int[]{4, 1, 1, 1};    // 4B, 1KB, 1MB, 1GB
  private static String[] UNITS = new String[]{"B", "KB", "MB", "GB"};
  private static final IntList BASE_FACTORS = IntArrayList.wrap(new int[]{2, 1});

  public static final MemoryAxisFormatter DEFAULT = new MemoryAxisFormatter(4, 10, 5);

  public MemoryAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold, true, x -> x < 0.01 ? 3 : x < 0.1 ? 2 : 1);
  }

  @Override
  public String getFormattedString(double globalRange, double value, boolean includeUnit) {
    // Value is passed in as the globalRange so that the value
    // itself is used to compute the correct unit/conversion factor
    return super.getFormattedString(value, value, includeUnit);
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
    return BASE;
  }

  @Override
  protected int getUnitMultiplier(int index) {
    return MULTIPLIER;
  }

  @Override
  protected int getUnitMinimalInterval(int index) {
    return MIN_INTERVALS[index];
  }

  @Override
  @NotNull
  protected IntList getUnitBaseFactors(int index) {
    return BASE_FACTORS;
  }
}
