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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.IntValueProperty;
import org.junit.Test;

public final class OptionalExpressionsTest {
  @Test
  public void testAsOptionalExpression() {
    IntProperty intProperty = new IntValueProperty(42);

    AsOptionalExpression<Integer> asOptionalExpr = new AsOptionalExpression<>(intProperty);

    assertThat(asOptionalExpr.get().get()).isEqualTo(42);

    intProperty.set(123);
    assertThat(asOptionalExpr.get().get()).isEqualTo(123);
  }
}
