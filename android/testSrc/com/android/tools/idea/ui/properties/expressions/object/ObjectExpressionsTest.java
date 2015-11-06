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
package com.android.tools.idea.ui.properties.expressions.object;

import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public final class ObjectExpressionsTest {

  @Test
  public void testFromOptionalExpression() {

    OptionalProperty<Integer> intProperty = OptionalProperty.absent();

    FromOptionalExpression<Integer, String> toStringExpr = new FromOptionalExpression<Integer, String>(intProperty) {
      @Override
      protected String transform(@NotNull Optional<Integer> intOpt) {
        if (!intOpt.isPresent()) return "";

        return intOpt.get().toString();
      }
    };

    assertThat(toStringExpr.get()).isEmpty();

    intProperty.setValue(123);
    assertThat(toStringExpr.get()).isEqualTo("123");

    intProperty.setValue(-9);
    assertThat(toStringExpr.get()).isEqualTo("-9");

    intProperty.clear();

    assertThat(toStringExpr.get()).isEmpty();
  }

  @Test
  public void testFromOptionalExpressionWithDefault() {

    OptionalProperty<Integer> intProperty = OptionalProperty.of(42);

    FromOptionalExpression<Integer, String> toStringExpr =
      new FromOptionalExpression.WithDefault<Integer, String>("(null int)", intProperty) {
        @Override
        protected String transform(@NotNull Integer intValue) {
          return intValue.toString();
        }
      };

    assertThat(toStringExpr.get()).isEqualTo("42");

    intProperty.clear();
    assertThat(toStringExpr.get()).isEqualTo("(null int)");
  }

  @Test
  public void testToOptionalExpression() {

    StringProperty strProperty = new StringValueProperty();

    ToOptionalExpression<String, Integer> toIntExpr = new ToOptionalExpression<String, Integer>(strProperty) {
      @Override
      protected Optional<Integer> transform(@NotNull String value) {
        try {
          return Optional.of(Integer.parseInt(value));
        }
        catch (NumberFormatException e) {
          return Optional.absent();
        }
      }
    };

    assertThat(toIntExpr.get().isPresent()).isFalse();

    strProperty.set("10");
    assertThat(toIntExpr.get().get()).isEqualTo(10);

    strProperty.set("-9000");
    assertThat(toIntExpr.get().get()).isEqualTo(-9000);

    strProperty.set("taco");
    assertThat(toIntExpr.get().isPresent()).isFalse();
  }
}
