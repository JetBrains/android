/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ui;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import javax.annotation.Nullable;
import javax.swing.JFormattedTextField;
import javax.swing.text.NumberFormatter;

/** Naive extension of JTextField, accepting integers or null. */
public class IntegerTextField extends JFormattedTextField {

  private static final NumberFormatter integerFormatter =
      new NumberFormatter(new NullableNumberFormat(NumberFormat.getIntegerInstance()));

  static {
    integerFormatter.setValueClass(Integer.class);
  }

  private static class NullableNumberFormat extends NumberFormat {

    private final NumberFormat base;

    private NullableNumberFormat(NumberFormat base) {
      this.base = base;
    }

    @Override
    public Object parseObject(String source) throws ParseException {
      if (source == null || source.trim().isEmpty()) {
        return null;
      }
      return super.parseObject(source);
    }

    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
      return base.format(number, toAppendTo, pos);
    }

    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
      return base.format(number, toAppendTo, pos);
    }

    @Override
    public Number parse(String source, ParsePosition parsePosition) {
      return base.parse(source, parsePosition);
    }
  }

  private int minValue = Integer.MIN_VALUE;
  private int maxValue = Integer.MAX_VALUE;

  public IntegerTextField() {
    super(integerFormatter);
  }

  @Override
  public void setValue(Object value) {
    if (value == null) {
      super.setValue(value);
      return;
    }
    Integer integer = parseValue(value);
    if (integer == null) {
      return; // retain existing value if invalid
    }
    super.setValue(integer < minValue ? minValue : integer > maxValue ? maxValue : integer);
  }

  @Nullable
  public Integer getIntValue() {
    return parseValue(getValue());
  }

  @Nullable
  private Integer parseValue(@Nullable Object value) {
    try {
      return value == null ? null : Integer.parseInt(getFormatter().valueToString(value));
    } catch (ParseException | NumberFormatException e) {
      return null;
    }
  }

  @CanIgnoreReturnValue
  public IntegerTextField setMinValue(int minValue) {
    this.minValue = minValue;
    return this;
  }

  @CanIgnoreReturnValue
  public IntegerTextField setMaxValue(int maxValue) {
    this.maxValue = maxValue;
    return this;
  }
}
