/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui.properties.constraints;

import com.android.tools.idea.ui.properties.core.IntValueProperty;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link com.android.tools.idea.ui.properties.AbstractProperty.Constraint}
 */
public class ConstraintsTest {
  @Test
  public void testGreaterThanOne() throws Exception {
    IntValueProperty greaterThanOneValue = new IntValueProperty(5);
    greaterThanOneValue.addConstraint(value -> Math.max(1, value));
    assertThat(greaterThanOneValue.get()).isEqualTo(5);

    greaterThanOneValue.set(-5);
    assertThat(greaterThanOneValue.get()).isEqualTo(1);

    greaterThanOneValue.set(105);
    assertThat(greaterThanOneValue.get()).isEqualTo(105);
  }

  @Test
  public void testNegative() throws Exception {
    IntValueProperty negativeValue = new IntValueProperty(5);
    negativeValue.addConstraint(value -> Math.min(0, value));
    assertThat(negativeValue.get()).isEqualTo(0);

    negativeValue.set(-5);
    assertThat(negativeValue.get()).isEqualTo(-5);

    negativeValue.set(105);
    assertThat(negativeValue.get()).isEqualTo(0);
  }

  @Test
  public void testPercent() throws Exception {
    IntValueProperty percentValue = new IntValueProperty(-5);
    percentValue.addConstraint(value -> Math.max(0, Math.min(100, value)));
    assertThat(percentValue.get()).isEqualTo(0);

    percentValue.set(-5);
    assertThat(percentValue.get()).isEqualTo(0);

    percentValue.set(105);
    assertThat(percentValue.get()).isEqualTo(100);

    percentValue.set(42);
    assertThat(percentValue.get()).isEqualTo(42);
  }
}