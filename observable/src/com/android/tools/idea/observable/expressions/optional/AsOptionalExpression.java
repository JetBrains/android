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
package com.android.tools.idea.observable.expressions.optional;

import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.expressions.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Expression for converting a target concrete value into an optional value. The optional will
 * always be present, which at first glance seems unnecessary, but it can be quite useful for
 * binding an optional property to some concrete input.
 */
public final class AsOptionalExpression<T> extends Expression<Optional<T>> {
  private final ObservableValue<? extends T> myValue;

  public AsOptionalExpression(ObservableValue<? extends T> value) {
    super(value);
    myValue = value;
  }

  @NotNull
  @Override
  public Optional<T> get() {
    return Optional.of(myValue.get());
  }
}
