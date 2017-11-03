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
package com.android.tools.idea.observable.expressions.string;

import com.android.tools.idea.observable.ObservableValue;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * An expression which returns a formatted string which takes target {@link ObservableValue}s as
 * arguments.
 */
public final class FormatExpression extends StringExpression {
  private final List<ObservableValue> myValues;
  @NotNull private final String myFormatString;

  public FormatExpression(@NotNull String formatString, ObservableValue... values) {
    super(values);
    myFormatString = formatString;
    myValues = Arrays.asList(values);
  }

  @NotNull
  @Override
  public String get() {
    Object[] values = new Object[myValues.size()];
    int i = 0;
    for (ObservableValue observableValue : myValues) {
      values[i++] = observableValue.get();
    }
    return String.format(myFormatString, values);
  }

}
