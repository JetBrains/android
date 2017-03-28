/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.properties.adapters;

import com.android.tools.idea.ui.properties.AbstractProperty;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;

/**
 * Adapter property that wraps a String type which represents a Double value.

 * If a string is passed in that isn't properly formatted, this adapter returns the last known
 * good value.
 */
public final class StringToDoubleAdapterProperty extends AdapterProperty<String, Double> {

  @NotNull private final DecimalFormat myFormat;

  /**
   * Defaults to 1 decimal point of precision.
   */
  public StringToDoubleAdapterProperty(@NotNull AbstractProperty<String> wrappedProperty) {
    this(wrappedProperty, 1);
  }

  public StringToDoubleAdapterProperty(@NotNull AbstractProperty<String> wrappedProperty, int numDecimals) {
    this(wrappedProperty, numDecimals, numDecimals);
  }

  public StringToDoubleAdapterProperty(@NotNull AbstractProperty<String> wrappedProperty, int numDecimals, int maxDecimals) {
    super(wrappedProperty, 0d);
    if (maxDecimals < numDecimals) {
      throw new IllegalArgumentException("maxDecimals must be larger or equal to numDecimals");
    }
    myFormat = new DecimalFormat("0." + StringUtil.repeat("0", numDecimals) + StringUtil.repeat("#", maxDecimals - numDecimals),
                                 new DecimalFormatSymbols());
  }

  @Nullable
  @Override
  protected Double convertFromSourceType(@NotNull String value) {
    try {
      ParsePosition pos = new ParsePosition(0);
      Number number = myFormat.parse(value, pos);
      if (number != null && pos.getIndex() == value.length()) {
        return number.doubleValue();
      }
    }
    catch (NumberFormatException ignored) {
    }
    return null;
  }

  @NotNull
  @Override
  protected String convertFromDestType(@NotNull Double value) {
    return myFormat.format(value);
  }
}

