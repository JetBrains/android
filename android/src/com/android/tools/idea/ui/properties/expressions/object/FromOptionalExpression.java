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
 * Base class for expressions that convert some optional target value into a concrete value.
 *
 * @param <S> The optional source type we're converting from
 * @param <D> The concrete dest type we're converting to
 */
public abstract class FromOptionalExpression<S, D> extends Expression implements ObservableValue<D> {

  private final ObservableOptional<S> myValue;

  public FromOptionalExpression(ObservableOptional<S> optional) {
    super(optional);
    myValue = optional;
  }

  @NotNull
  @Override
  public final D get() {
    return transform(myValue.get());
  }

  protected abstract D transform(@NotNull Optional<S> optional);

  public abstract static class WithDefault<S, D> extends FromOptionalExpression<S, D> {
    @NotNull private final D myDefaultValue;

    public WithDefault(@NotNull D defaultValue, ObservableOptional<S> optionalValue) {
      super(optionalValue);
      myDefaultValue = defaultValue;
    }

    @Override
    protected final D transform(@NotNull Optional<S> optional) {
      if (!optional.isPresent()) {
        return myDefaultValue;
      }

      return transform(optional.get());
    }

    protected abstract D transform(@NotNull S value);
  }
}
