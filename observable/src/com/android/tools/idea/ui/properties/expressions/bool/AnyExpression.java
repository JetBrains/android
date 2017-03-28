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
package com.android.tools.idea.ui.properties.expressions.bool;

import com.android.tools.idea.ui.properties.ObservableValue;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * An expression which returns true if any of a list of target values is true.
 */
public final class AnyExpression extends BooleanExpression {

  private final ObservableValue<Boolean>[] myValues;

  public AnyExpression(ObservableValue<Boolean>... values) {
    super(values);
    myValues = values;
  }

  public AnyExpression(Collection<? extends ObservableValue<Boolean>> values) {
    //noinspection unchecked
    this(Iterables.toArray(values, ObservableValue.class));
  }

  @NotNull
  @Override
  public Boolean get() {
    for (ObservableValue<Boolean> value : myValues) {
      if (value.get()) {
        return true;
      }
    }

    return false;
  }
}
