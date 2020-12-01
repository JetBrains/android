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

public class EnergyAxisFormatter extends BaseAxisFormatter {
  public static final String[] LABELS = {"Light", "Medium", "Heavy"};

  public static final int DEFAULT_MAJOR_INTERVAL = 200;

  // Default formatter for the Energy Axis value. As the Axis may display a label at the max value, this formatter would not show duplicate
  // labels at the max value and second max value.
  public static final EnergyAxisFormatter DEFAULT = new EnergyAxisFormatter(0, LABELS.length, 1);
  // Energy legend formatter that displays the value independent of the axis markers.
  public static final EnergyAxisFormatter LEGEND_FORMATTER = new EnergyAxisFormatter(0, LABELS.length, 1) {
    @NotNull
    @Override
    public String getFormattedString(double globalRange, double value, boolean includeUnit) {
      if (value < DEFAULT_MAJOR_INTERVAL * LABELS.length) {
        return super.getFormattedString(globalRange, value, includeUnit);
      }
      return LABELS[LABELS.length -1];
    }
  };

  private EnergyAxisFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  @NotNull
  @Override
  public String getFormattedString(double globalRange, double value, boolean includeUnit) {
    int index = (int) Math.ceil(value / DEFAULT_MAJOR_INTERVAL);
    if (index <= 0) {
      return "None";
    }
    if (index <= LABELS.length) {
      return LABELS[index - 1];
    }
    long majorInterval = getMajorInterval(globalRange);
    double previousMarkerValue = value % majorInterval != 0 ? value - value % majorInterval : value - majorInterval;
    // The previous marker does not cover the largest label so returns the largest label.
    if (previousMarkerValue <= DEFAULT_MAJOR_INTERVAL * (LABELS.length - 1)) {
      return LABELS[LABELS.length - 1];
    }
    return "";
  }

  /**
   * Returns the major interval value for drawing the markers, given a global range. If the global range is too large to show all markers,
   * the number of markers is at most {@code LABELS.length} but can be smaller to fit the range.
   */
  @Override
  public long getMajorInterval(double range) {
    int numTicks = (int) (range / DEFAULT_MAJOR_INTERVAL);
    if (numTicks < 1) {
      return (long) range;
    }
    if (numTicks <= LABELS.length) {
      return DEFAULT_MAJOR_INTERVAL;
    }
    // Reduces the number of ticks until it is 2, otherwise there is only one marker which is the range.
    final int largestMarkerValue = LABELS.length * DEFAULT_MAJOR_INTERVAL;
    for (numTicks = LABELS.length - 1; numTicks > 1; numTicks--) {
      long interval = largestMarkerValue / numTicks;
      // Find the smallest interval that will cover the range (that's beyond max), as to keep as many labels as possible.
      // We will keep trying until we reduce down to 2 labels, after which we give up and use the max label.
      if (range < largestMarkerValue + interval) {
        return interval;
      }
    }
    // Since we have a value that exceeds the expected range, we need to round up as to avoid feedback issues with returning
    // a major interval that is smaller than the range.
    return largestMarkerValue;
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
