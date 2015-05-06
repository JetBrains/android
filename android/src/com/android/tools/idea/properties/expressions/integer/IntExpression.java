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
import com.android.tools.idea.properties.expressions.bool.BooleanExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Interface which, when implemented, allows the chaining of Integer values.
 */
public interface IntExpression extends ObservableValue<Integer> {
  @NotNull
  BooleanExpression isEqualTo(@NotNull ObservableValue<Integer> value);

  @NotNull
  BooleanExpression isGreaterThan(@NotNull ObservableValue<Integer> value);

  @NotNull
  BooleanExpression isLessThan(@NotNull ObservableValue<Integer> value);

  @NotNull
  BooleanExpression isGreaterThanEqualTo(@NotNull ObservableValue<Integer> value);

  @NotNull
  BooleanExpression isLessThanEqualTo(@NotNull ObservableValue<Integer> value);
}
