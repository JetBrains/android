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

import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.expressions.bool.AndExpression;
import com.android.tools.idea.observable.expressions.bool.NotExpression;
import com.android.tools.idea.observable.expressions.bool.OrExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only handle to a {@link BoolProperty}.
 */
public interface ObservableBool extends ObservableValue<Boolean> {
  ObservableBool TRUE = new ConstantBool() {
    @Override
    @NotNull
    public Boolean get() {
      return Boolean.TRUE;
    }
  };
  ObservableBool FALSE = new ConstantBool() {
    @Override
    @NotNull
    public Boolean get() {
      return Boolean.FALSE;
    }
  };

  @NotNull
  default ObservableBool not() {
    return new NotExpression(this);
  }

  @NotNull
  default ObservableBool or(@NotNull ObservableValue<Boolean> other) {
    return new OrExpression(this, other);
  }

  @NotNull
  default ObservableBool and(@NotNull ObservableValue<Boolean> other) {
    return new AndExpression(this, other);
  }
}
