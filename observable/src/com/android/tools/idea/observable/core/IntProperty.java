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
package com.android.tools.idea.observable.core;

import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.expressions.integer.ComparisonExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Base class that every integer-type property should inherit from, as it provides useful methods
 * that enable chaining.
 */
public abstract class IntProperty extends AbstractProperty<Integer> implements ObservableInt {

  public void increment() {
    set(get() + 1);
  }

  @NotNull
  @Override
  public ObservableBool isEqualTo(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isEqual(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isGreaterThan(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isGreaterThan(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isGreaterThanEqualTo(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isGreaterThanEqual(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isLessThan(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isLessThan(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isLessThanEqualTo(@NotNull ObservableValue<Integer> value) {
    return ComparisonExpression.isLessThanEqual(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isEqualTo(int value) {
    return ComparisonExpression.isEqual(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isGreaterThan(int value) {
    return ComparisonExpression.isGreaterThan(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isGreaterThanEqualTo(int value) {
    return ComparisonExpression.isGreaterThanEqual(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isLessThan(int value) {
    return ComparisonExpression.isLessThan(this, value);
  }

  @NotNull
  @Override
  public ObservableBool isLessThanEqualTo(int value) {
    return ComparisonExpression.isLessThanEqual(this, value);
  }
}
