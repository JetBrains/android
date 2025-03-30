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
package com.android.tools.idea.observable.expressions;

import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.IntValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.string.StringExpression;
import com.google.common.truth.Truth;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public final class ExpressionTest {
  @Test(expected = IllegalArgumentException.class)
  public void expressionsNeedAtLeastOneObservable() throws Exception {
    final StringProperty value = new StringValueProperty();
    Expression expr = new StringExpression() { // This should be "new StringExpression(value)"
      @NotNull
      @Override
      public String get() {
        return value.get();
      }
    };
  }

  @Test
  public void testSimpleExpression() throws Exception {
    final IntProperty intValue = new IntValueProperty(13);
    final Expression<String> intToString = new Expression<String>(intValue) {
      @NotNull
      @Override
      public String get() {
        return intValue.get().toString();
      }
    };

    Truth.assertThat(intToString.get()).isEqualTo("13");

    intValue.set(-13);
    Truth.assertThat(intToString.get()).isEqualTo("-13");
  }
}
