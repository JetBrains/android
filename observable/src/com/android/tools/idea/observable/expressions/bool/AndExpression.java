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
import org.jetbrains.annotations.NotNull;

/**
 * An expression which converts two booleans to their logical AND result.
 */
public final class AndExpression extends BooleanExpression {
  private final ObservableValue<Boolean> myValueLhs;
  private final ObservableValue<Boolean> myValueRhs;

  public AndExpression(@NotNull ObservableValue<Boolean> valueLhs, @NotNull ObservableValue<Boolean> valueRhs) {
    super(valueLhs, valueRhs);
    myValueLhs = valueLhs;
    myValueRhs = valueRhs;
  }

  @NotNull
  @Override
  public Boolean get() {
    return myValueLhs.get() && myValueRhs.get();
  }
}
