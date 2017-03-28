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
package com.android.tools.idea.ui.properties.expressions.string;

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.IntValueProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import org.junit.Test;

import static com.android.tools.idea.ui.properties.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY;
import static com.google.common.truth.Truth.assertThat;

public final class StringExpressionsTest {

  @Test
  public void testFormatStringExpression() throws Exception {
    IntValueProperty arg1 = new IntValueProperty(42);
    BoolValueProperty arg2 = new BoolValueProperty(true);
    StringValueProperty destValue = new StringValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(destValue, new FormatExpression("The answer is %1$s. Hitchhiker reference? %2$s", arg1, arg2));

    assertThat(destValue.get()).isEqualTo("The answer is 42. Hitchhiker reference? true");

    arg1.set(2);
    arg2.set(false);

    assertThat(destValue.get()).isEqualTo("The answer is 2. Hitchhiker reference? false");
  }

  @Test
  public void testTrimExpression() throws Exception {
    StringValueProperty srcValue = new StringValueProperty();
    StringValueProperty destValue = new StringValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(destValue, srcValue.trim());
    assertThat(destValue.get()).isEmpty();

    srcValue.set("    Preceded by whitespace");
    assertThat(destValue.get()).isEqualTo("Preceded by whitespace");

    srcValue.set(" Surrounded by whitespace ");
    assertThat(destValue.get()).isEqualTo("Surrounded by whitespace");

    srcValue.set("    ");
    assertThat(destValue.get()).isEmpty();

    srcValue.set(" \t  \n ");
    assertThat(destValue.get()).isEmpty();
  }
}