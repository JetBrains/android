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

public class IntRangeConstraintTest {
  static IntRangeConstraint atLeast(long value) {
    return new IntRangeConstraint(value, Long.MAX_VALUE);
  }

  static IntRangeConstraint atMost(long value) {
    return new IntRangeConstraint(Long.MIN_VALUE, value);
  }

  static IntRangeConstraint range(long from, long to) {
    return new IntRangeConstraint(from, to);
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
  public void testCompareIntWithFloat() {
    assertThat(range(1,5).contains(FloatRangeConstraintTest.range(2, 4)).isValid()).isTrue();
    assertThat(range(1,5).contains(FloatRangeConstraintTest.range(2, 5)).isValid()).isTrue();
    assertThat(range(1,5).contains(FloatRangeConstraintTest.range(2, 6)).isValid()).isFalse();
    assertThat(range(1,5).contains(FloatRangeConstraintTest.range(1, 5)).isValid()).isTrue();
    assertThat(range(1,5).contains(FloatRangeConstraintTest.range(0, 5)).isValid()).isFalse();
  }

  @Test
  public void testCompareFloatWithInt() {
    assertThat(FloatRangeConstraintTest.range(1,5).contains(range(2, 4)).isValid()).isTrue();
    assertThat(FloatRangeConstraintTest.range(1,5).contains(range(2, 5)).isValid()).isTrue();
    assertThat(FloatRangeConstraintTest.range(1,5).contains(range(2, 6)).isValid()).isFalse();
    assertThat(FloatRangeConstraintTest.range(1,5).contains(range(1, 5)).isValid()).isTrue();
    assertThat(FloatRangeConstraintTest.range(1,5).contains(range(0, 5)).isValid()).isFalse();
  }
}
