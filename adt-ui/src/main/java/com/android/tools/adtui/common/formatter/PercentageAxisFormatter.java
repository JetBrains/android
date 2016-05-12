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

import com.android.annotations.NonNull;

public class PercentageAxisFormatter extends SingleUnitAxisFormatter {

  private static final int UNIT_MINIMAL_INTERVAL = 10;

  private static final String PERCENTAGE_UNIT = "%";

  private static final PercentageAxisFormatter DEFAULT = new PercentageAxisFormatter(10, 10, 1 /* No larger scale, so we can set anything */);

  public PercentageAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  @NonNull
  @Override
  protected String getUnit(int index) {
    return PERCENTAGE_UNIT;
  }

  @Override
  protected int getUnitMinimalInterval(int index) {
    return UNIT_MINIMAL_INTERVAL;
  }

  @NonNull
  @Override
  public String getFormattedString(double globalRange, double value) {
    return String.format("%d%s", Math.round(value / getMultiplier()), PERCENTAGE_UNIT);
  }

  @NonNull
  public static PercentageAxisFormatter getDefault() {
    return DEFAULT;
  }
}
