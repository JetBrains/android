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
package com.android.tools.idea.ui.properties.expressions.object;

import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.ObservableOptional;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for expressions that convert some concrete target value into an optional value.
 *
 * @param <S> The concrete source type we're converting from
 * @param <D> The optional dest type we're converting to
 */
public abstract class ToOptionalExpression<S, D> extends Expression implements ObservableOptional<D> {
  private final ObservableValue<S> myValue;

  public ToOptionalExpression(@NotNull ObservableValue<S> value) {
    super(value);
    myValue = value;
  }

  @NotNull
  @Override
  public final Optional<D> get() {
    return transform(myValue.get());
  }

  protected abstract Optional<D> transform(@NotNull S value);
}
