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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * A string container that delegates to another string for toString. Used in drop downs.
 */
public class ValueWithDisplayString {
  public static final ValueWithDisplayString SEPARATOR = new ValueWithDisplayString("-", "-");
  public static final ValueWithDisplayString UNSET = new ValueWithDisplayString("none", null);

  private final String myDisplayString;
  private final String myValue;
  private final String myHint;
  private final ValueSelector mySelector;
  private boolean myUseValueForToString;

  public ValueWithDisplayString(@Nullable String displayString, @Nullable String value) {
    this(displayString, value, null);
  }

  public ValueWithDisplayString(@Nullable String displayString, @Nullable String value, @Nullable String hint) {
    this(displayString, value, hint, null);
  }

  public ValueWithDisplayString(@Nullable String displayString,
                                @Nullable String value,
                                @Nullable String hint,
                                @Nullable ValueSelector selector) {
    myDisplayString = StringUtil.notNullize(displayString);
    myValue = value;
    myHint = hint;
    mySelector = selector;
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

  @Nullable
  public String getHint() {
    return myHint;
  }

  @Nullable
  public ValueSelector getValueSelector() {
    return mySelector;
  }

  /**
   * This value is a hack to get around a problem where swing will call {@link DefaultComboBoxModel#setSelectedItem}
   * with the toString() value of this class.
   * See comment in {@link com.android.tools.idea.uibuilder.property.editors.NlEnumEditor#setModel}.
   */
  public void setUseValueForToString(boolean useValueForToString) {
    myUseValueForToString = useValueForToString;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myValue, myDisplayString, myHint);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ValueWithDisplayString)) {
      return false;
    }
    ValueWithDisplayString value = (ValueWithDisplayString)other;
    return Objects.equals(myValue, value.myValue) &&
           Objects.equals(myDisplayString, value.myDisplayString) &&
           Objects.equals(myHint, value.myHint);
  }

  public interface ValueSelector {
    @Nullable
    ValueWithDisplayString selectValue(@Nullable String currentValue);
  }
}
