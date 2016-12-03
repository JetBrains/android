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

public class SizeConstraintTest {
  private static SizeConstraint exactly(long value) {
    return new SizeConstraint(value, Long.MIN_VALUE, Long.MAX_VALUE, 1);
  }

  private static SizeConstraint atLeast(long value) {
    return new SizeConstraint(-1, value, Long.MAX_VALUE, 1);
  }

  private static SizeConstraint atMost(long value) {
    return new SizeConstraint(-1, Long.MIN_VALUE, value, 1);
  }

  private static SizeConstraint range(long from, long to) {
    return new SizeConstraint(-1, from, to, 1);
  }

  private static SizeConstraint multiple(int multiple) {
    return new SizeConstraint(-1, Long.MIN_VALUE, Long.MAX_VALUE, multiple);
  }

  private static SizeConstraint rangeWithMultiple(long from, long to, int multiple) {
    return new SizeConstraint(-1, from, to, multiple);
  }

  private static SizeConstraint minWithMultiple(long from, int multiple) {
    return new SizeConstraint(-1, from, Long.MAX_VALUE, multiple);
  }

  @Test
  public void testExactly() {
    assertThat(exactly(3).contains(exactly(3)).isValid()).isTrue();
    assertThat(exactly(3).contains(exactly(4)).isValid()).isFalse();
  }

  @Test
  public void testRangeContainsExactly() {
    assertThat(range(2,4).contains(exactly(3)).isValid()).isTrue();
    assertThat(range(2,4).contains(exactly(2)).isValid()).isTrue();
    assertThat(range(2,4).contains(exactly(4)).isValid()).isTrue();
    assertThat(range(2,4).contains(exactly(5)).isValid()).isFalse();
    assertThat(range(2,4).contains(exactly(1)).isValid()).isFalse();
    assertThat(range(2,2).contains(exactly(2)).isValid()).isTrue();
  }

  @Test
  public void testMinContainsExactly() {
    assertThat(atLeast(2).contains(exactly(3)).isValid()).isTrue();
    assertThat(atLeast(3).contains(exactly(3)).isValid()).isTrue();
    assertThat(atLeast(4).contains(exactly(3)).isValid()).isFalse();
  }

  @Test
  public void testMaxContainsExactly() {
    assertThat(atMost(4).contains(exactly(3)).isValid()).isTrue();
    assertThat(atMost(3).contains(exactly(3)).isValid()).isTrue();
    assertThat(atMost(2).contains(exactly(3)).isValid()).isFalse();
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
  public void testMultiples() {
    assertThat(multiple(8).contains(multiple(8)).isValid()).isTrue();
    assertThat(multiple(8).contains(multiple(4)).isValid()).isFalse();
    assertThat(multiple(8).contains(multiple(16)).isValid()).isTrue();
    assertThat(multiple(8).contains(multiple(1)).isValid()).isFalse();
    assertThat(multiple(8).contains(multiple(32)).isValid()).isTrue();
    assertThat(multiple(8).contains(multiple(33)).isValid()).isFalse();

    assertThat(rangeWithMultiple(20, 100, 5).contains(exactly(20)).isValid()).isTrue();
    assertThat(rangeWithMultiple(20, 100, 5).contains(exactly(21)).isValid()).isFalse();

    assertThat(rangeWithMultiple(20, 100, 5).contains(rangeWithMultiple(20, 40, 5)).isValid()).isTrue();
    assertThat(rangeWithMultiple(20, 100, 5).contains(rangeWithMultiple(20, 40, 10)).isValid()).isTrue();
    assertThat(rangeWithMultiple(20, 100, 5).contains(rangeWithMultiple(20, 40, 3)).isValid()).isFalse();
    assertThat(rangeWithMultiple(20, 100, 5).contains(range(20, 40)).isValid()).isFalse();
    assertThat(rangeWithMultiple(40, 100, 5).contains(range(40, 60)).isValid()).isFalse();
    assertThat(range(20, 100).contains(rangeWithMultiple(20, 40, 2)).isValid()).isTrue();
  }
}
