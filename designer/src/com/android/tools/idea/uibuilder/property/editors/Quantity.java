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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Quantity implements Comparable<Quantity> {
  private static final Pattern QUANTITY_PATTERN = Pattern.compile("^(\\d+(\\.\\d+)?)(.*)$");

  private final int myValue;
  private final String myUnit;

  @Nullable
  public static Quantity parse(@NotNull String value) {
    Matcher matcher = QUANTITY_PATTERN.matcher(value);
    if (!matcher.matches()) {
      return null;
    }
    try {
      return new Quantity(Integer.parseInt(matcher.group(1)), matcher.group(3));
    }
    catch (NumberFormatException ignore) {
      return null;  // Format this value as if this was not a value with a unit
    }
  }

  @NotNull
  public static String addUnit(@NotNull NlProperty property, @NotNull String value) {
    AttributeDefinition definition = property.getDefinition();
    boolean isDimension = definition != null && definition.getFormats().contains(AttributeFormat.Dimension);
    if (!isDimension) {
      return value;
    }
    Quantity quantity = parse(value);
    if (quantity == null || !quantity.myUnit.isEmpty()) {
      return value;
    }
    switch (property.getName()) {
      case SdkConstants.ATTR_TEXT_SIZE:
      case SdkConstants.ATTR_LINE_SPACING_EXTRA:
        return quantity.myValue + SdkConstants.UNIT_SP;
      default:
        return quantity.myValue + SdkConstants.UNIT_DP;
    }
  }

  private Quantity(int value, @NotNull String unit) {
    myValue = value;
    myUnit = unit;
  }

  private int getValue() {
    return myValue;
  }

  @NotNull
  private String getUnit() {
    return myUnit;
  }

  @Override
  public int compareTo(@Nullable Quantity other) {
    if (other == null) {
      return -1;
    }
    return Comparator
      .comparing(Quantity::getUnit)
      .thenComparing(Quantity::getValue)
      .compare(this, other);
  }
}
