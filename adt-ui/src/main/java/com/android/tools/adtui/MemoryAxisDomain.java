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

public final class MemoryAxisDomain extends BaseAxisDomain {

  private static final int MULTIPLIER = 1024;
  private static final int BASE = 2;
  private static final int[] MIN_INTERVALS = new int[]{4, 1, 1};    // 4KB, 1MB, 1GB
  private static String[] UNITS = new String[]{"KB", "MB", "GB"};
  private static final TIntArrayList BASE_FACTORS = new TIntArrayList(new int[]{2, 1});
  public static final MemoryAxisDomain DEFAULT = new MemoryAxisDomain(10, 50, 5);

  public MemoryAxisDomain(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
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
  @NonNull
  protected TIntArrayList getUnitBaseFactors(int index) {
    return BASE_FACTORS;
  }
}
