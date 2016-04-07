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

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import org.junit.Test;

import static com.android.tools.idea.ui.properties.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY;
import static org.fest.assertions.Assertions.assertThat;

public final class BooleanExpressionsTest {

  @Test
  public void testInvariants() throws Exception {
    assertThat(BooleanExpressions.alwaysTrue().get()).isTrue();
    assertThat(BooleanExpressions.alwaysFalse().get()).isFalse();
  }

  @Test
  public void testNotExpression() throws Exception {

    BoolValueProperty srcValue = new BoolValueProperty(true);
    BoolValueProperty destValue = new BoolValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    bindings.bind(destValue, BooleanExpressions.not(srcValue));

    assertThat(srcValue.get()).isTrue();
    assertThat(destValue.get()).isFalse();

    srcValue.set(false);
    assertThat(srcValue.get()).isFalse();
    assertThat(destValue.get()).isTrue();
  }

  @Test
  public void testAndExpression() throws Exception {
    BoolValueProperty srcValue1 = new BoolValueProperty();
    BoolValueProperty srcValue2 = new BoolValueProperty();
    BoolValueProperty destValue = new BoolValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(destValue, srcValue1.and(srcValue2));

    assertThat(srcValue1.get()).isFalse();
    assertThat(srcValue2.get()).isFalse();
    assertThat(destValue.get()).isFalse();

    srcValue1.set(true);
    srcValue2.set(false);
    assertThat(srcValue1.get()).isTrue();
    assertThat(srcValue2.get()).isFalse();
    assertThat(destValue.get()).isFalse();

    srcValue1.set(false);
    srcValue2.set(true);
    assertThat(srcValue1.get()).isFalse();
    assertThat(srcValue2.get()).isTrue();
    assertThat(destValue.get()).isFalse();

    srcValue1.set(true);
    srcValue2.set(true);
    assertThat(srcValue1.get()).isTrue();
    assertThat(srcValue2.get()).isTrue();
    assertThat(destValue.get()).isTrue();
  }

  @Test
  public void testOrExpression() throws Exception {
    BoolValueProperty srcValue1 = new BoolValueProperty();
    BoolValueProperty srcValue2 = new BoolValueProperty();
    BoolValueProperty destValue = new BoolValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(destValue, srcValue1.or(srcValue2));

    assertThat(srcValue1.get()).isFalse();
    assertThat(srcValue2.get()).isFalse();
    assertThat(destValue.get()).isFalse();

    srcValue1.set(true);
    srcValue2.set(false);
    assertThat(srcValue1.get()).isTrue();
    assertThat(srcValue2.get()).isFalse();
    assertThat(destValue.get()).isTrue();

    srcValue1.set(false);
    srcValue2.set(true);
    assertThat(srcValue1.get()).isFalse();
    assertThat(srcValue2.get()).isTrue();
    assertThat(destValue.get()).isTrue();

    srcValue1.set(true);
    srcValue2.set(true);
    assertThat(srcValue1.get()).isTrue();
    assertThat(srcValue2.get()).isTrue();
    assertThat(destValue.get()).isTrue();
  }

  @Test
  public void testIsEqualToExpression() throws Exception {

    StringValueProperty srcValue = new StringValueProperty("Initial Value");
    BoolValueProperty destValue = new BoolValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(destValue, srcValue.isEqualTo("Modified Value"));

    assertThat(destValue.get()).isFalse();

    srcValue.set("Modified Value");
    assertThat(destValue.get()).isTrue();

    srcValue.set("Final Value");
    assertThat(destValue.get()).isFalse();
  }

  @Test
  public void testAnyExpression() throws Exception {
    BoolValueProperty srcValue1 = new BoolValueProperty();
    BoolValueProperty srcValue2 = new BoolValueProperty();
    BoolValueProperty srcValue3 = new BoolValueProperty();
    BoolValueProperty destValue = new BoolValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(destValue, BooleanExpressions.any(srcValue1, srcValue2, srcValue3));

    assertThat(srcValue1.get()).isFalse();
    assertThat(srcValue2.get()).isFalse();
    assertThat(srcValue3.get()).isFalse();
    assertThat(destValue.get()).isFalse();

    srcValue1.set(true);
    srcValue2.set(false);
    srcValue3.set(false);
    assertThat(srcValue1.get()).isTrue();
    assertThat(srcValue2.get()).isFalse();
    assertThat(srcValue3.get()).isFalse();
    assertThat(destValue.get()).isTrue();

    srcValue1.set(false);
    srcValue2.set(true);
    srcValue3.set(false);
    assertThat(srcValue1.get()).isFalse();
    assertThat(srcValue2.get()).isTrue();
    assertThat(srcValue3.get()).isFalse();
    assertThat(destValue.get()).isTrue();

    srcValue1.set(true);
    srcValue2.set(false);
    srcValue3.set(true);
    assertThat(srcValue1.get()).isTrue();
    assertThat(srcValue2.get()).isFalse();
    assertThat(srcValue3.get()).isTrue();
    assertThat(destValue.get()).isTrue();

    srcValue1.set(true);
    srcValue2.set(true);
    srcValue3.set(true);
    assertThat(srcValue1.get()).isTrue();
    assertThat(srcValue2.get()).isTrue();
    assertThat(srcValue3.get()).isTrue();
    assertThat(destValue.get()).isTrue();

    srcValue1.set(false);
    srcValue2.set(false);
    srcValue3.set(false);
    assertThat(srcValue1.get()).isFalse();
    assertThat(srcValue2.get()).isFalse();
    assertThat(srcValue3.get()).isFalse();
    assertThat(destValue.get()).isFalse();
  }

  @Test
  public void testIsEmptyStringExpression() throws Exception {
    StringValueProperty srcValue = new StringValueProperty();
    BoolValueProperty destValue = new BoolValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(destValue, srcValue.isEmpty());
    assertThat(destValue.get()).isTrue();

    srcValue.set("Not Empty");
    assertThat(destValue.get()).isFalse();

    srcValue.set("    ");
    assertThat(destValue.get()).isFalse();

    srcValue.set("");
    assertThat(destValue.get()).isTrue();
  }
}