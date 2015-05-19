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
import com.android.tools.idea.ui.properties.core.ObservableBool;
import org.jetbrains.annotations.NotNull;

/**
 * Miscellaneous convenience methods which, when imported statically, can provide readability to
 * chained expressions.
 */
public final class BooleanExpressions {
  public static final ObservableBool TRUE = new BooleanExpression() {
    @NotNull
    @Override
    public Boolean get() {
      return true;
    }
  };

  public static final ObservableBool FALSE = new BooleanExpression() {
    @NotNull
    @Override
    public Boolean get() {
      return false;
    }
  };

  public static ObservableBool any(ObservableValue<Boolean>... values) {
    return new AnyExpression(values);
  }

  public static ObservableBool not(@NotNull ObservableValue<Boolean> value) {
    return new NotExpression(value);
  }
}
