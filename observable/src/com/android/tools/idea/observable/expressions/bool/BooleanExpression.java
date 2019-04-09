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

import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.Expression;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for boolean expressions, providing a default implementation for the {@link ObservableBool} interface.
 */
public abstract class BooleanExpression extends Expression<Boolean> implements ObservableBool {
  public static final ObservableBool ALWAYS_TRUE = new ConstantBool() {
    @Override
    @NotNull
    public Boolean get() {
      return Boolean.TRUE;
    }
  };
  public static final ObservableBool ALWAYS_FALSE = new ConstantBool() {
    @Override
    @NotNull
    public Boolean get() {
      return Boolean.FALSE;
    }
  };

  protected BooleanExpression(@NotNull ObservableValue... values) {
    super(values);
  }

  private static abstract class ConstantBool implements ObservableBool {
    @Override
    public void addListener(@NotNull InvalidationListener listener) {
      // No need to notify the listener since the value never changes.
    }

    @Override
    public void removeListener(@NotNull InvalidationListener listener) {
      // No need to notify the listener since the value never changes.
    }

    @Override
    public void addWeakListener(@NotNull InvalidationListener listener) {
      // No need to notify the listener since the value never changes.
    }
  }
}
