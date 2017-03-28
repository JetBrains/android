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
package com.android.tools.idea.ui.properties.expressions.value;

import com.android.tools.idea.ui.properties.core.ObservableOptional;
import com.android.tools.idea.ui.properties.expressions.Expression;
import org.jetbrains.annotations.NotNull;

/**
 * A useful base-class expression for converting optional values to concrete values.
 *
 * @param <S> The optional source type we're converting from
 * @param <D> The concrete dest type we're converting to
 */
public abstract class TransformOptionalExpression<S, D> extends Expression<D> {

  @NotNull private final D myDefaultValue;
  private final ObservableOptional<S> myValue;

  public TransformOptionalExpression(@NotNull D defaultValue, @NotNull ObservableOptional<S> optional) {
    super(optional);
    myDefaultValue = defaultValue;
    myValue = optional;
  }

  @NotNull
  @Override
  public final D get() {
    if (!myValue.get().isPresent()) {
      return myDefaultValue;
    }
    return transform(myValue.getValue());
  }

  @NotNull
  protected abstract D transform(@NotNull S value);
}
