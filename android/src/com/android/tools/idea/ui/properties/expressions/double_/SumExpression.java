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
package com.android.tools.idea.ui.properties.expressions.double_;

import com.android.tools.idea.ui.properties.ObservableValue;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * An expression which returns a sum of all {@link ObservableValue<Double>} arguments.
 */
public final class SumExpression extends DoubleExpression {
  private final List<ObservableValue<Double>> myValues;

  public SumExpression(ObservableValue<Double>... values) {
    super(values);
    myValues = Arrays.asList(values);
  }

  @NotNull
  @Override
  public Double get() {
    Double sum = 0.0;
    for (ObservableValue<Double> value : myValues) {
      sum += value.get();
    }
    return sum;
  }
}
