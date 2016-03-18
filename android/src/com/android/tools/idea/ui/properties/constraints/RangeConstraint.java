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
package com.android.tools.idea.ui.properties.constraints;

import com.android.tools.idea.ui.properties.ObservableProperty;
import org.jetbrains.annotations.NotNull;

/**
 * A constraint that enforces a value be clamped between two numbers (inclusive).
 */
public final class RangeConstraint implements ObservableProperty.Constraint<Integer> {
  public static RangeConstraint forPercents() {
    return new RangeConstraint(0, 100);
  }

  private final int myMin;
  private final int myMax;

  public RangeConstraint(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException(String.format("Bad range constraint, min > max (%d$0, %d$1)", min, max));
    }
    myMin = min;
    myMax = max;
  }

  @NotNull
  @Override
  public Integer constrain(@NotNull Integer value) {
    int i = value.intValue();
    return i < myMin ? myMin : (i > myMax ? myMax : value);
  }
}
