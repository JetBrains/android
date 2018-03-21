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

import com.android.tools.idea.common.property.NlProperty;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SimpleEnumSupport extends EnumSupport {
  private final List<String> myPossibleValues;

  public SimpleEnumSupport(@NotNull NlProperty property, @NotNull List<String> possibleValues) {
    super(property);
    myPossibleValues = possibleValues;
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    List<ValueWithDisplayString> values = new ArrayList<>(myPossibleValues.size());
    for (String stringValue : myPossibleValues) {
      values.add(new ValueWithDisplayString(stringValue, stringValue));
    }
    return values;
  }
}
