/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.intellij.ui.components.JBScrollBar;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * A custom scrollbar that represents time unit and synchronizes with its global and view ranges.
 */
public class RangeTimeScrollBar extends JBScrollBar {
  /**
   * Mapping to minimum and maximum values of this scrollbar.
   * @see #setValues(int, int, int, int)
   */
  @NotNull
  private final Range myGlobalRange;

  /**
   * Mapping to the current and extent values of this scrollbar.
   * @see #setValues(int, int, int, int)
   */
  @NotNull
  private final Range myViewRange;

  /**
   * TimeUnit of {@link #myGlobalRange} and {@link #myViewRange}.
   */
  @NotNull
  private final TimeUnit myUnit;

  @NotNull
  private final AspectObserver myObserver;

  private boolean myUpdating;

  public RangeTimeScrollBar(@NotNull Range globalRange, @NotNull Range viewRange, @NotNull TimeUnit unit) {
    super(HORIZONTAL);
    myGlobalRange = globalRange;
    myViewRange = viewRange;
    myUnit = unit;

    setUI(new RangeScrollBarUI());
    addAdjustmentListener(e -> updateViewRange());

    myObserver = new AspectObserver();
    myGlobalRange.addDependency(myObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
    myViewRange.addDependency(myObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
    rangeChanged();
  }

  private void rangeChanged() {
    myUpdating = true;
    // We convert the given time ranges to milliseconds to prevent from integer overflow,
    // because JScrollBar API based on ints.
    int globalLengthMs = unitToMs(myGlobalRange.getLength());

    Range intersection = myGlobalRange.getIntersection(myViewRange);
    if (!intersection.isEmpty()) {
      int viewLengthMs = unitToMs(intersection.getLength());
      int viewRelativeMinMs = unitToMs(intersection.getMin() - myGlobalRange.getMin());
      setValues(viewRelativeMinMs, viewLengthMs, 0, globalLengthMs);
    } else {
      setValues(myViewRange.getMax() < myGlobalRange.getMin() ? 0 : globalLengthMs, 0, 0, globalLengthMs);
    }

    myUpdating = false;
  }

  private void updateViewRange() {
    if (myUpdating) {
      return;
    }
    int valueMs = getValue();
    int viewRelativeMinMs = unitToMs(Math.max(0, (myViewRange.getMin() - myGlobalRange.getMin())));
    double delta = msToUnit(valueMs - viewRelativeMinMs);
    myViewRange.shift(delta);
  }

  private int unitToMs(double duration) {
    return (int)myUnit.toMillis((long)duration);
  }

  private double msToUnit(double duration) {
    return myUnit.convert((long)duration, TimeUnit.MILLISECONDS);
  }
}
