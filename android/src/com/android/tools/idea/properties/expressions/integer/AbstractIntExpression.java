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
package com.android.tools.idea.properties.expressions.integer;

import com.android.tools.idea.properties.ObservableValue;
import com.android.tools.idea.properties.expressions.Expression;
import com.android.tools.idea.properties.expressions.bool.BooleanExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for Integer expressions, providing a default implementation for the {@link IntExpression} interface.
 */
public abstract class AbstractIntExpression extends Expression<Integer> implements IntExpression {

  protected AbstractIntExpression(ObservableValue... values) {
    super(values);
  }

  @NotNull
  @Override
  public BooleanExpression isEqualTo(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isEqual(this, value);
  }

  @NotNull
  @Override
  public BooleanExpression isGreaterThan(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isGreaterThan(this, value);
  }

  @NotNull
  @Override
  public BooleanExpression isGreaterThanEqualTo(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isGreaterThanEqual(this, value);
  }

  @NotNull
  @Override
  public BooleanExpression isLessThan(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isLessThan(this, value);
  }

  @NotNull
  @Override
  public BooleanExpression isLessThanEqualTo(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isLessThanEqual(this, value);
  }
}
