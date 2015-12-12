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

import com.android.tools.idea.ui.properties.core.IntProperty;
import com.android.tools.idea.ui.properties.core.IntValueProperty;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.ui.properties.expressions.value.AsValueExpression;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public final class OptionalExpressionsTest {
  @Test
  public void testAsOptionalExpression() {
    IntProperty intProperty = new IntValueProperty(42);

    AsOptionalExpression<Integer> asOptionalExpr = new AsOptionalExpression<Integer>(intProperty);

    assertThat(asOptionalExpr.get().get()).isEqualTo(42);

    intProperty.set(123);
    assertThat(asOptionalExpr.get().get()).isEqualTo(123);
  }
}
