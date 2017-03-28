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
package org.jetbrains.android.inspections;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class FloatRangeConstraintTest {
  static FloatRangeConstraint atLeast(double value) {
    return new FloatRangeConstraint(value, Long.MAX_VALUE, true, true);
  }

  static FloatRangeConstraint atMost(double value) {
    return new FloatRangeConstraint(Long.MIN_VALUE, value, true, true);
  }

  static FloatRangeConstraint range(double from, double to) {
    return new FloatRangeConstraint(from, to, true, true);
  }

  static FloatRangeConstraint greaterThan(double from) {
    return new FloatRangeConstraint(from, Long.MAX_VALUE, false, true);
  }

  static FloatRangeConstraint lessThan(double to) {
    return new FloatRangeConstraint(Long.MIN_VALUE, to, true, false);
  }

  @Test
  public void testRangeContainsRange() {
    assertThat(range(1,5).contains(range(2, 4)).isValid()).isTrue();
    assertThat(range(1,5).contains(range(2, 5)).isValid()).isTrue();
    assertThat(range(1,5).contains(range(2, 6)).isValid()).isFalse();
    assertThat(range(1,5).contains(range(1, 5)).isValid()).isTrue();
    assertThat(range(1,5).contains(range(0, 5)).isValid()).isFalse();
  }

  @Test
  public void testMinContainsMin() {
    assertThat(atLeast(2).contains(atLeast(3)).isValid()).isTrue();
    assertThat(atLeast(2).contains(atLeast(2)).isValid()).isTrue();
    assertThat(atLeast(2).contains(atLeast(1)).isValid()).isFalse();
  }

  @Test
  public void testMaxContainsMax() {
    assertThat(atMost(4).contains(atMost(3)).isValid()).isTrue();
    assertThat(atMost(4).contains(atMost(4)).isValid()).isTrue();
    assertThat(atMost(4).contains(atMost(5)).isValid()).isFalse();
  }

  @Test
  public void testInvalid() {
    // Ranges don't contain open intervals
    assertThat(atMost(4).contains(atLeast(1)).isValid()).isFalse();
    assertThat(atLeast(4).contains(atMost(4)).isValid()).isFalse();
    assertThat(range(1,4).contains(atLeast(1)).isValid()).isFalse();
    assertThat(range(1,4).contains(atMost(4)).isValid()).isFalse();
  }

  @Test
  public void testMixedEndpoints() {
    assertThat(atLeast(4).contains(atLeast(4)).isValid()).isTrue();
    assertThat(atLeast(4).contains(atLeast(3.9999)).isValid()).isFalse();
    assertThat(atLeast(4).contains(atLeast(4.0001)).isValid()).isTrue();
    assertThat(greaterThan(4).contains(atLeast(4.0001)).isValid()).isTrue();
    assertThat(greaterThan(4).contains(atLeast(4)).isValid()).isFalse();

    assertThat(atMost(4).contains(atMost(4)).isValid()).isTrue();
    assertThat(atMost(4).contains(atMost(4.0001)).isValid()).isFalse();
    assertThat(atMost(4).contains(atMost(3.9999)).isValid()).isTrue();
    assertThat(lessThan(4).contains(atMost(3.9999)).isValid()).isTrue();
    assertThat(lessThan(4).contains(atMost(4)).isValid()).isFalse();
    assertThat(lessThan(4).contains(lessThan(4)).isValid()).isTrue();
  }

  @Test
  public void testCompareFloatWithIntRange() {
    assertThat(atLeast(4).contains(IntRangeConstraintTest.atLeast(4)).isValid()).isTrue();
    assertThat(atLeast(4).contains(IntRangeConstraintTest.atLeast(3)).isValid()).isFalse();
    assertThat(atLeast(4).contains(IntRangeConstraintTest.atLeast(5)).isValid()).isTrue();
    assertThat(greaterThan(4).contains(IntRangeConstraintTest.atLeast(5)).isValid()).isTrue();
    assertThat(greaterThan(4).contains(IntRangeConstraintTest.atLeast(4)).isValid()).isFalse();

    assertThat(atMost(4).contains(IntRangeConstraintTest.atMost(4)).isValid()).isTrue();
    assertThat(atMost(4).contains(IntRangeConstraintTest.atMost(5)).isValid()).isFalse();
    assertThat(atMost(4).contains(IntRangeConstraintTest.atMost(3)).isValid()).isTrue();
    assertThat(lessThan(4).contains(IntRangeConstraintTest.atMost(3)).isValid()).isTrue();
    assertThat(lessThan(4).contains(IntRangeConstraintTest.atMost(4)).isValid()).isFalse();
  }

  @Test
  public void testCompareIntWithFloatRange() {
    assertThat(IntRangeConstraintTest.atLeast(4).contains(atLeast(4)).isValid()).isTrue();
    assertThat(IntRangeConstraintTest.atLeast(4).contains(atLeast(3.9999)).isValid()).isFalse();
    assertThat(IntRangeConstraintTest.atLeast(4).contains(atLeast(4.0001)).isValid()).isTrue();
    assertThat(IntRangeConstraintTest.atLeast(4).contains(greaterThan(5)).isValid()).isTrue();
    assertThat(IntRangeConstraintTest.atLeast(4).contains(greaterThan(4)).isValid()).isFalse();

    assertThat(IntRangeConstraintTest.atMost(4).contains(atMost(4)).isValid()).isTrue();
    assertThat(IntRangeConstraintTest.atMost(4).contains(atMost(4.0001)).isValid()).isFalse();
    assertThat(IntRangeConstraintTest.atMost(4).contains(atMost(3.9999)).isValid()).isTrue();
    assertThat(IntRangeConstraintTest.atMost(4).contains(lessThan(4)).isValid()).isFalse();
  }
}
