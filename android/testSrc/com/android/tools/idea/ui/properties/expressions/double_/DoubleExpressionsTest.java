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
package com.android.tools.idea.ui.properties.expressions.double_;

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.DoubleValueProperty;
import org.junit.Test;

import static com.android.tools.idea.ui.properties.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY;
import static org.fest.assertions.Assertions.assertThat;

public final class DoubleExpressionsTest {
  @Test
  public void testIsEqualExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    DoubleValueProperty rhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isEqualTo(rhs));

    assertThat(result.get()).isTrue();

    lhs.set(10.0);
    assertThat(result.get()).isFalse();

    rhs.set(10.0);
    assertThat(result.get()).isTrue();

    rhs.set(20.0);
    assertThat(result.get()).isFalse();
  }

  @Test
  public void testLessThanExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    DoubleValueProperty rhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isLessThan(rhs));

    assertThat(result.get()).isFalse();

    rhs.set(10.0);
    assertThat(result.get()).isTrue();

    lhs.set(10.0);
    assertThat(result.get()).isFalse();
  }

  @Test
  public void testGreaterThanExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    DoubleValueProperty rhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isGreaterThan(rhs));

    assertThat(result.get()).isFalse();

    lhs.set(10.0);
    assertThat(result.get()).isTrue();

    rhs.set(10.0);
    assertThat(result.get()).isFalse();
  }

  @Test
  public void testLessThanEqualExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    DoubleValueProperty rhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isLessThanEqualTo(rhs));

    assertThat(result.get()).isTrue();

    lhs.set(10.0);
    assertThat(result.get()).isFalse();

    rhs.set(10.0);
    assertThat(result.get()).isTrue();

    rhs.set(20.0);
    assertThat(result.get()).isTrue();
  }

  @Test
  public void testGreaterThanEqualExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    DoubleValueProperty rhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isGreaterThanEqualTo(rhs));

    assertThat(result.get()).isTrue();

    rhs.set(10.0);
    assertThat(result.get()).isFalse();

    lhs.set(10.0);
    assertThat(result.get()).isTrue();

    rhs.set(20.0);
    assertThat(result.get()).isFalse();
  }

  @Test
  public void testIsEqualWithValueExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isEqualTo(10));

    assertThat(result.get()).isFalse();

    lhs.set(10.0);
    assertThat(result.get()).isTrue();

    lhs.set(20.0);
    assertThat(result.get()).isFalse();
  }

  @Test
  public void testLessThanValueExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isLessThan(10.0));

    assertThat(result.get()).isTrue();

    lhs.set(10.0);
    assertThat(result.get()).isFalse();

    lhs.set(20.0);
    assertThat(result.get()).isFalse();
  }

  @Test
  public void testGreaterThanValueExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isGreaterThan(10.0));

    assertThat(result.get()).isFalse();

    lhs.set(10.0);
    assertThat(result.get()).isFalse();

    lhs.set(20.0);
    assertThat(result.get()).isTrue();
  }

  @Test
  public void testLessThanEqualValueExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isLessThanEqualTo(10.0));

    assertThat(result.get()).isTrue();

    lhs.set(10.0);
    assertThat(result.get()).isTrue();

    lhs.set(20.0);
    assertThat(result.get()).isFalse();
  }

  @Test
  public void testGreaterThanEqualValueExpression() {
    BoolValueProperty result = new BoolValueProperty();
    DoubleValueProperty lhs = new DoubleValueProperty();
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(result, lhs.isGreaterThanEqualTo(10.0));

    assertThat(result.get()).isFalse();

    lhs.set(10.0);
    assertThat(result.get()).isTrue();

    lhs.set(20.0);
    assertThat(result.get()).isTrue();
  }

  @Test
  public void testSumExpression() {
    DoubleValueProperty sum = new DoubleValueProperty();
    DoubleValueProperty a = new DoubleValueProperty(1.0);
    DoubleValueProperty b = new DoubleValueProperty(2.0);
    DoubleValueProperty c = new DoubleValueProperty(3.0);
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);

    bindings.bind(sum, new com.android.tools.idea.ui.properties.expressions.double_.SumExpression(a, b, c));

    assertThat(sum.get()).isEqualTo(6.0);

    a.set(10.0);
    b.set(100.0);
    c.set(1000.0);

    assertThat(sum.get()).isEqualTo(1110.0);

    c.set(1001.0);

    assertThat(sum.get()).isEqualTo(1111);
  }
}
