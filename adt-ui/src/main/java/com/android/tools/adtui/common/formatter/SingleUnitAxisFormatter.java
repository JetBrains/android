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
package com.android.tools.adtui.common.formatter;

import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Axis domain to be used by axis components with only one unit.
 * E.g. Number of threads or usage of CPU (%).
 */
public class SingleUnitAxisFormatter extends BaseAxisFormatter {

  private static final int MULTIPLIER = 1;

  private static final int BASE = 10;

  private static final int UNITS_COUNT = 1;

  private static final TIntArrayList BASE_FACTORS = new TIntArrayList(new int[]{1});

  private final int mUnitMinimalInterval;

  private final String mUnit;

  public SingleUnitAxisFormatter(int maxMinorTicks, int maxMajorTicks, int unitMinimalInterval, String unit) {
    super(maxMinorTicks, maxMajorTicks, 1 /* No larger scale, so we can set anything */);
    mUnitMinimalInterval = unitMinimalInterval;
    mUnit = unit;
  }

  @Override
  protected int getNumUnits() {
    return UNITS_COUNT;
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
  @NotNull
  protected TIntArrayList getUnitBaseFactors(int index) {
    return BASE_FACTORS;
  }

  @NotNull
  @Override
  protected String getUnit(int index) {
    return mUnit;
  }

  @Override
  protected int getUnitMinimalInterval(int index) {
    return mUnitMinimalInterval;
  }

  @NotNull
  @Override
  public String getFormattedString(double globalRange, double value) {
    return String.format("%d%s", Math.round(value / getMultiplier()), mUnit);
  }
}
