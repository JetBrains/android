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
package com.android.tools.idea.ui.properties.expressions.integer;

import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.IntValueProperty;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import org.jetbrains.annotations.NotNull;

/**
 * A base class for expressions that do numerical comparisons with ints.
 */
public abstract class ComparisonExpression extends BooleanExpression {
  private final ObservableValue<Integer> myValueLhs;
  private final ObservableValue<Integer> myValueRhs;

  public static ObservableBool isEqual(@NotNull ObservableValue<Integer> valueLhs, @NotNull ObservableValue<Integer> valueRhs) {
    return new ComparisonExpression(valueLhs, valueRhs) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs == valueRhs;
      }
    };
  }

  public static ObservableBool isGreaterThan(@NotNull ObservableValue<Integer> valueLhs, @NotNull ObservableValue<Integer> valueRhs) {
    return new ComparisonExpression(valueLhs, valueRhs) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs > valueRhs;
      }
    };
  }

  public static ObservableBool isGreaterThanEqual(@NotNull ObservableValue<Integer> valueLhs, @NotNull ObservableValue<Integer> valueRhs) {
    return new ComparisonExpression(valueLhs, valueRhs) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs >= valueRhs;
      }
    };
  }

  public static ObservableBool isLessThan(@NotNull ObservableValue<Integer> valueLhs, @NotNull ObservableValue<Integer> valueRhs) {
    return new ComparisonExpression(valueLhs, valueRhs) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs < valueRhs;
      }
    };
  }

  public static ObservableBool isLessThanEqual(@NotNull ObservableValue<Integer> valueLhs, @NotNull ObservableValue<Integer> valueRhs) {
    return new ComparisonExpression(valueLhs, valueRhs) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs <= valueRhs;
      }
    };
  }

  public static ObservableBool isEqual(@NotNull ObservableValue<Integer> valueLhs, int valueRhs) {
    return new ComparisonExpression(valueLhs, new IntValueProperty(valueRhs)) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs == valueRhs;
      }
    };
  }

  public static ObservableBool isGreaterThan(@NotNull ObservableValue<Integer> valueLhs, int valueRhs) {
    return new ComparisonExpression(valueLhs, new IntValueProperty(valueRhs)) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs > valueRhs;
      }
    };
  }

  public static ObservableBool isGreaterThanEqual(@NotNull ObservableValue<Integer> valueLhs, int valueRhs) {
    return new ComparisonExpression(valueLhs, new IntValueProperty(valueRhs)) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs >= valueRhs;
      }
    };
  }

  public static ObservableBool isLessThan(@NotNull ObservableValue<Integer> valueLhs, int valueRhs) {
    return new ComparisonExpression(valueLhs, new IntValueProperty(valueRhs)) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs < valueRhs;
      }
    };
  }

  public static ObservableBool isLessThanEqual(@NotNull ObservableValue<Integer> valueLhs, int valueRhs) {
    return new ComparisonExpression(valueLhs, new IntValueProperty(valueRhs)) {
      @Override
      protected Boolean compare(int valueLhs, int valueRhs) {
        return valueLhs <= valueRhs;
      }
    };
  }

  protected ComparisonExpression(@NotNull ObservableValue<Integer> valueLhs, @NotNull ObservableValue<Integer> valueRhs) {
    super(valueLhs, valueRhs);
    myValueLhs = valueLhs;
    myValueRhs = valueRhs;
  }

  @NotNull
  @Override
  public final Boolean get() {
    return compare(myValueLhs.get(), myValueRhs.get());
  }

  protected abstract Boolean compare(int valueLhs, int valueRhs);
}
