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
package com.android.tools.idea.ui.properties.expressions.optional;

import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Expression for converting a target concrete value into an optional value. The optional will
 * always be present, which at first glance seems unnecessary, but it can be quite useful for
 * binding an optional property to some concrete input.
 */
public final class AsOptionalExpression<T> extends Expression<Optional<T>> {
  private final ObservableValue<T> myValue;

  public AsOptionalExpression(ObservableValue<T> value) {
    super(value);
    myValue = value;
  }

  @NotNull
  @Override
  public Optional<T> get() {
    return Optional.of(myValue.get());
  }
}
