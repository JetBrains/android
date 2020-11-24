/*
 * Copyright (C) 2019 The Android Open Source Project
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

public class UserCounterAxisFormatter extends BaseAxisFormatter {

  // TODO: (b/140522169) Base default interval based on how many events users have registered.
  public static final int DEFAULT_MAJOR_INTERVAL = 2;
  public static String[] LABELS = {"Light", "Medium", "Heavy"};
  // Default formatter for the User Counter Axis value.
  public static final UserCounterAxisFormatter DEFAULT = new UserCounterAxisFormatter(1, LABELS.length, 1);

  private UserCounterAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  @NotNull
  @Override
  public String getFormattedString(double globalRange, double value, boolean includeUnit) {
    int index = (int)Math.ceil(value / LABELS.length);

    if (index <= 0) {
      return "None";
    }

    if (index <= LABELS.length) {
      return LABELS[index - 1];
    }
    else {
      // If number exceeds LABELS size, returns the last label (heavy).
      return LABELS[DEFAULT_MAJOR_INTERVAL];
    }
  }

  @Override
  protected int getNumUnits() {
    return LABELS.length;
  }

  @NotNull
  @Override
  protected String getUnit(int index) {
    return index < LABELS.length ? LABELS[index] : "";
  }

  @Override
  protected int getUnitBase(int index) {
    return 1;
  }

  @Override
  protected int getUnitMultiplier(int index) {
    return 1;
  }

  @Override
  protected int getUnitMinimalInterval(int index) {
    return 1;
  }

  @NotNull
  @Override
  protected IntList getUnitBaseFactors(int index) {
    return new IntArrayList(0);
  }
}
