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

import com.android.tools.idea.ui.properties.ObservableProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter property that wraps a String type which represents a Double value.
 */
public final class StringToDoubleAdapterProperty extends AdapterProperty<String, Double> {
  @NotNull private final String myFormatString;

  /**
   * Defaults to 1 decimal point of precision.
   */
  public StringToDoubleAdapterProperty(@NotNull ObservableProperty<String> wrappedProperty) {
    this(wrappedProperty, 1);
  }

  public StringToDoubleAdapterProperty(@NotNull ObservableProperty<String> wrappedProperty, int numDecimals) {
    super(wrappedProperty);
    myFormatString = "%1$." + numDecimals + "f";
  }

  @NotNull
  @Override
  protected Double convertFromSourceType(@NotNull String value) {
    try {
      return Double.parseDouble(value);
    }
    catch (NumberFormatException e) {
      return 0.0;
    }
  }

  @NotNull
  @Override
  protected String convertFromDestType(@NotNull Double value) {
    return String.format(myFormatString, value);
  }
}

