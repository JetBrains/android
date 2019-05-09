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
package com.android.tools.idea.observable.adapters;

import static com.android.utils.DecimalUtils.trimInsignificantZeros;

import com.android.tools.idea.observable.AbstractProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter property that wraps a String type which represents a Double value.

 * If a string is passed in that isn't properly formatted, this adapter returns the last known
 * good value.
 */
public final class StringToDoubleAdapterProperty extends AdapterProperty<String, Double> {
  @NotNull private final DecimalFormat myFormat;
  @NotNull private final DecimalFormatSymbols mySymbols;

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
    this(wrappedProperty, createFormat(numDecimals, maxDecimals));
  }

  public StringToDoubleAdapterProperty(@NotNull AbstractProperty<String> wrappedProperty, @NotNull DecimalFormat format) {
    super(wrappedProperty, 0.);
    myFormat = format;
    mySymbols = format.getDecimalFormatSymbols();
  }

  private static DecimalFormat createFormat(int numDecimals, int maxDecimals) {
    Preconditions.checkArgument(maxDecimals >= numDecimals, "maxDecimals may not be less than numDecimals");
    String pattern = numDecimals == 0 ? "0" : "0." + Strings.repeat("0", numDecimals) + Strings.repeat("#", maxDecimals - numDecimals);
    return new DecimalFormat(pattern, new DecimalFormatSymbols());
  }

  @Override
  @Nullable
  protected Double convertFromSourceType(@NotNull String value) {
    value = value.trim();
    ParsePosition pos = new ParsePosition(0);
    Number number = myFormat.parse(value, pos);
    if (number == null || pos.getIndex() != value.length()) {
      return null;
    }
    return number.doubleValue();
  }

  @Override
  @NotNull
  protected String convertFromDestType(@NotNull Double value) {
    return trimInsignificantZeros(myFormat.format(value), mySymbols);
  }
}

