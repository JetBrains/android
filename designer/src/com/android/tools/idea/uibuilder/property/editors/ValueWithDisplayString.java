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
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold a value with a different display value.
 * Used in combo-box and dropdown style controls.
 */
public class ValueWithDisplayString {
  public static final String TEXT_APPEARANCE_SUFFIX = "TextAppearance";
  public static final ValueWithDisplayString SEPARATOR = new ValueWithDisplayString("-", "-");
  public static final ValueWithDisplayString UNSET = new ValueWithDisplayString("none", null);
  public static final ValueWithDisplayString[] EMPTY_ARRAY = new ValueWithDisplayString[0];

  private static final Pattern TEXT_APPEARANCE_PATTERN = Pattern.compile("^((@(\\w+:)?)style/)?TextAppearance.(.+)$");

  private final String myDisplayString;
  private final String myValue;
  private boolean myUseValueForToString;

  public static ValueWithDisplayString[] create(@NotNull String[] values) {
    ValueWithDisplayString[] array = new ValueWithDisplayString[values.length];
    int index = 0;
    for (String value : values) {
      array[index++] = new ValueWithDisplayString(value, value);
    }
    return array;
  }

  public static ValueWithDisplayString[] create(@NotNull List<String> values) {
    ValueWithDisplayString[] array = new ValueWithDisplayString[values.size()];
    int index = 0;
    for (String value : values) {
      array[index++] = new ValueWithDisplayString(value, value);
    }
    return array;
  }

  public static ValueWithDisplayString create(@Nullable String value, @NotNull NlProperty property) {
    String display = property.resolveValue(value);
    if (display == null) {
      return UNSET;
    }
    if (property.getName().equals(SdkConstants.ATTR_TEXT_APPEARANCE) || property.getName().endsWith(TEXT_APPEARANCE_SUFFIX)) {
      ValueWithDisplayString attr = createStyleValue(display, value);
      if (attr != null) {
        return attr;
      }
    }
    return new ValueWithDisplayString(display, value);
  }

  public static ValueWithDisplayString createStyleValue(@NotNull String styleName,
                                                        @Nullable String value) {
    String display = styleName;
    Matcher matcher = TEXT_APPEARANCE_PATTERN.matcher(styleName);
    if (matcher.matches()) {
      display = matcher.group(4);
    }
    return new ValueWithDisplayString(display, value);
  }

  public ValueWithDisplayString(@Nullable String displayString, @Nullable String value) {
    myDisplayString = StringUtil.notNullize(displayString);
    myValue = value;
  }

  @Override
  @NotNull
  public String toString() {
    return myUseValueForToString ? StringUtil.notNullize(myValue) : myDisplayString;
  }

  @NotNull
  public String getDisplayString() {
    return myDisplayString;
  }

  @Nullable
  public String getValue() {
    return myValue;
  }

  public void setUseValueForToString(boolean useValueForToString) {
    myUseValueForToString = useValueForToString;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myValue, myDisplayString);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ValueWithDisplayString)) {
      return false;
    }
    return Objects.equals(myValue, ((ValueWithDisplayString)other).myValue) &&
           Objects.equals(myDisplayString, ((ValueWithDisplayString)other).myDisplayString);
  }
}
