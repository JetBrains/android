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
package com.android.tools.adtui.model;

import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class RangeTest {

  /**
   * Delta to be used in double comparison.
   * Note: this value is set to 0 on purpose because currently all the numbers used in the tests are ints
   * and can be represented in a double without information loss.
   * If eventually a long is used in the tests, a small delta value should be used instead.
   */
  private static double DELTA = 0;

  @Test
  public void testSubtractAll() {
    Range range = new Range(0, 1);
    List<Range> subtract = range.subtract(new Range(-10, 10));
    assertThat(subtract).isEmpty();
  }

  @Test
  public void testSubtractInner() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(2, 5));
    assertThat(subtract).hasSize(2);
    assertThat(subtract.get(0).getMin()).isWithin(DELTA).of(0);
    assertThat(subtract.get(0).getMax()).isWithin(DELTA).of(2);
    assertThat(subtract.get(1).getMin()).isWithin(DELTA).of(5);
    assertThat(subtract.get(1).getMax()).isWithin(DELTA).of(10);
  }

  @Test
  public void testSubtractLeft() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(-5, 5));
    assertThat(subtract).hasSize(1);
    assertThat(subtract.get(0).getMin()).isWithin(DELTA).of(5);
    assertThat(subtract.get(0).getMax()).isWithin(DELTA).of(10);
  }

  @Test
  public void testSubtractRight() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(5, 15));
    assertThat(subtract).hasSize(1);
    assertThat(subtract.get(0).getMin()).isWithin(DELTA).of(0);
    assertThat(subtract.get(0).getMax()).isWithin(DELTA).of(5);
  }

  @Test
  public void testSubtractNone() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(15, 25));
    assertThat(subtract).hasSize(1);
    assertThat(subtract.get(0).getMin()).isWithin(DELTA).of(0);
    assertThat(subtract.get(0).getMax()).isWithin(DELTA).of(10);
  }

  @Test
  public void testSubtractEmpty() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(5, 5));
    assertThat(subtract).hasSize(2);
    assertThat(subtract.get(0).getMin()).isWithin(DELTA).of(0);
    assertThat(subtract.get(0).getMax()).isWithin(DELTA).of(5);
    assertThat(subtract.get(1).getMin()).isWithin(DELTA).of(5);
    assertThat(subtract.get(1).getMax()).isWithin(DELTA).of(10);
  }

  @Test
  public void testSubtractFromEmpty() {
    Range range = new Range(0, 0);
    List<Range> subtract = range.subtract(new Range(-5, 5));
    assertThat(subtract).isEmpty();
  }

  @Test
  public void testSubtractFromPointOutside() {
    Range range = new Range(10, 10);
    List<Range> subtract = range.subtract(new Range(25, 50));
    assertThat(subtract.get(0).getMin()).isWithin(DELTA).of(10);
    assertThat(subtract.get(0).getMax()).isWithin(DELTA).of(10);
  }

  @Test
  public void testInnerIntersection() {
    Range range = new Range(0, 10);
    Range intersection = range.getIntersection(new Range(3, 5));
    assertThat(intersection.getMin()).isWithin(DELTA).of(3);
    assertThat(intersection.getMax()).isWithin(DELTA).of(5);
  }

  @Test
  public void testLeftIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(-3, 5));
    assertThat(intersection.getMin()).isWithin(DELTA).of(1);
    assertThat(intersection.getMax()).isWithin(DELTA).of(5);
  }

  @Test
  public void testRightIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(5, 15));
    assertThat(intersection.getMin()).isWithin(DELTA).of(5);
    assertThat(intersection.getMax()).isWithin(DELTA).of(10);
  }

  @Test
  public void testNoIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(15, 25));
    assertThat(intersection.isEmpty()).isTrue();
  }

  @Test
  public void testPointIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(5, 5));
    assertThat(intersection.getMin()).isWithin(DELTA).of(5);
    assertThat(intersection.getMax()).isWithin(DELTA).of(5);
  }

  @Test
  public void testIntersectionPoint() {
    Range range = new Range(5, 5);
    Range intersection = range.getIntersection(new Range(0, 10));
    assertThat(intersection.getMin()).isWithin(DELTA).of(5);
    assertThat(intersection.getMax()).isWithin(DELTA).of(5);
  }

  @Test
  public void testMinMax() {
    Range range = new Range(0, 5);
    assertThat(range.getMin()).isWithin(DELTA).of(0);
    assertThat(range.getMax()).isWithin(DELTA).of(5);

    range.setMin(3);
    assertThat(range.getMin()).isWithin(DELTA).of(3);
    range.setMax(20);
    assertThat(range.getMax()).isWithin(DELTA).of(20);

    range.set(8, 15);
    assertThat(range.getMin()).isWithin(DELTA).of(8);
    assertThat(range.getMax()).isWithin(DELTA).of(15);

    range.set(new Range(10, 30));
    assertThat(range.getMin()).isWithin(DELTA).of(10);
    assertThat(range.getMax()).isWithin(DELTA).of(30);

    assertThat(range.getLength()).isWithin(DELTA).of(20);
  }

  @Test
  public void testClamp() {
    Range range = new Range(20, 100);

    // Clamp to max
    assertThat(range.clamp(140)).isWithin(DELTA).of(100);

    // Clamp to min
    assertThat(range.clamp(10)).isWithin(DELTA).of(20);
  }

  @Test
  public void testShift() {
    Range range = new Range(0, 100);
    assertThat(range.getMin()).isWithin(DELTA).of(0);
    assertThat(range.getMax()).isWithin(DELTA).of(100);

    range.shift(50);
    assertThat(range.getMin()).isWithin(DELTA).of(50);
    assertThat(range.getMax()).isWithin(DELTA).of(150);

    range.shift(-100);
    assertThat(range.getMin()).isWithin(DELTA).of(-50);
    assertThat(range.getMax()).isWithin(DELTA).of(50);
  }

  @Test
  public void testExpand() {
    Range range = new Range(0, 100);
    assertThat(range.getMin()).isWithin(DELTA).of(0);
    assertThat(range.getMax()).isWithin(DELTA).of(100);

    range.expand(5, 30);
    assertThat(range.getMin()).isWithin(DELTA).of(0);
    assertThat(range.getMax()).isWithin(DELTA).of(100);

    range.expand(-20, 30);
    assertThat(range.getMin()).isWithin(DELTA).of(-20);
    assertThat(range.getMax()).isWithin(DELTA).of(100);

    range.expand(0, 200);
    assertThat(range.getMin()).isWithin(DELTA).of(-20);
    assertThat(range.getMax()).isWithin(DELTA).of(200);

    range.expand(-40, 250);
    assertThat(range.getMin()).isWithin(DELTA).of(-40);
    assertThat(range.getMax()).isWithin(DELTA).of(250);
  }

  @Test
  public void testContains() {
    Range range = new Range(0, 100);
    assertThat(range.contains(0)).isTrue();
    assertThat(range.contains(100)).isTrue();
    assertThat(range.contains(50)).isTrue();
    assertThat(range.contains(-0.5)).isFalse();
    assertThat(range.contains(100.5)).isFalse();
  }

  @Test
  public void testToString() {
    Range same1 = new Range(0, 100);
    Range same2 = new Range(0, 100);
    Range same3 = new Range(same2);
    Range different = new Range(1, 99);
    Range r = new Range(5, 10);
    Range empty = new Range();

    assertThat(same1.toString()).isEqualTo(same2.toString());
    assertThat(same1.toString()).isEqualTo(same3.toString());
    assertThat(same1.toString()).isNotEqualTo(different.toString());

    assertThat(r.toString()).isNotEqualTo(empty.toString());
    r.clear();
    assertThat(r.toString()).isEqualTo(empty.toString());
  }
}
