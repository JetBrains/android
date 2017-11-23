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
package com.android.tools.idea.observable.expressions.bool;

import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Base class for boolean expressions, providing a default implementation for the {@link ObservableBool} interface.
 */
public abstract class BooleanExpression extends Expression<Boolean> implements ObservableBool {
  public static final ObservableBool ALWAYS_TRUE = new ConstantBool(true);
  public static final ObservableBool ALWAYS_FALSE = new ConstantBool(false);

  protected BooleanExpression(ObservableValue... values) {
    super(values);
  }

  @NotNull
  @Override
  public final ObservableBool not() {
    return BooleanExpressions.not(this);
  }

  @NotNull
  @Override
  public final ObservableBool or(@NotNull ObservableValue<Boolean> other) {
    return new OrExpression(this, other);
  }

  @NotNull
  @Override
  public final ObservableBool and(@NotNull ObservableValue<Boolean> other) {
    return new AndExpression(this, other);
  }

  /**
   * Allows boolean expressions to be created using lambda syntax.
   */
  @NotNull
  public static BooleanExpression create(Supplier<Boolean> valueSupplier, ObservableValue... values) {
    return new BooleanExpression(values) {
      @Override
      @NotNull
      public Boolean get() {
        return valueSupplier.get();
      }
    };
  }

  private static class ConstantBool extends BoolProperty {
    private final Boolean myValue;

    private ConstantBool(Boolean value) {
      myValue = value;
    }

    @NotNull
    @Override
    public Boolean get() {
      return myValue;
    }

    @Override
    protected void setDirectly(@NotNull Boolean value) {
      throw new UnsupportedOperationException();
    }
  }
}
