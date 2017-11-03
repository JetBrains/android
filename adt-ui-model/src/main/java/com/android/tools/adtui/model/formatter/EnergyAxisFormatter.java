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

import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

public class EnergyAxisFormatter extends BaseAxisFormatter {
  public static final EnergyAxisFormatter SIMPLE = new EnergyAxisFormatter(1, 5, 10);
  public static final EnergyAxisFormatter DETAILED = new EnergyAxisFormatter(2, 5, 10);

  private static final int BASE = 10;
  private static final int MULTIPLIER = 1000;
  private static final int[] MIN_INTERVALS = new int[]{1, 1, 1};
  private static String[] UNITS = new String[]{"uAh", "mAh", "Ah"};
  private static final TIntArrayList BASE_FACTORS = new TIntArrayList(new int[]{1, 2, 5, 10});

  private EnergyAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  @Override
  protected int getNumUnits() {
    return UNITS.length;
  }

  @NotNull
  @Override
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

  @NotNull
  @Override
  protected TIntArrayList getUnitBaseFactors(int index) {
    return BASE_FACTORS;
  }
}
