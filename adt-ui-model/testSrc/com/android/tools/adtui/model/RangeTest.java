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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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
    assertThat(subtract, empty());
  }

  @Test
  public void testSubtractInner() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(2, 5));
    assertThat(subtract, hasSize(2));
    assertEquals(0, subtract.get(0).getMin(), DELTA);
    assertEquals(2, subtract.get(0).getMax(), DELTA);
    assertEquals(5, subtract.get(1).getMin(), DELTA);
    assertEquals(10, subtract.get(1).getMax(), DELTA);
  }

  @Test
  public void testSubtractLeft() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(-5, 5));
    assertThat(subtract, hasSize(1));
    assertEquals(5, subtract.get(0).getMin(), DELTA);
    assertEquals(10, subtract.get(0).getMax(), DELTA);
  }

  @Test
  public void testSubtractRight() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(5, 15));
    assertThat(subtract, hasSize(1));
    assertEquals(0, subtract.get(0).getMin(), DELTA);
    assertEquals(5, subtract.get(0).getMax(), DELTA);
  }

  @Test
  public void testSubtractNone() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(15, 25));
    assertThat(subtract, hasSize(1));
    assertEquals(0, subtract.get(0).getMin(), DELTA);
    assertEquals(10, subtract.get(0).getMax(), DELTA);
  }

  @Test
  public void testSubtractEmpty() {
    Range range = new Range(0, 10);
    List<Range> subtract = range.subtract(new Range(5, 5));
    assertThat(subtract, hasSize(2));
    assertEquals(0, subtract.get(0).getMin(), DELTA);
    assertEquals(5, subtract.get(0).getMax(), DELTA);
    assertEquals(5, subtract.get(1).getMin(), DELTA);
    assertEquals(10, subtract.get(1).getMax(), DELTA);
  }

  @Test
  public void testSubtractFromEmpty() {
    Range range = new Range(0, 0);
    List<Range> subtract = range.subtract(new Range(-5, 5));
    assertThat(subtract, empty());
  }

  @Test
  public void testSubtractFromPointOutside() {
    Range range = new Range(10, 10);
    List<Range> subtract = range.subtract(new Range(25, 50));
    assertEquals(10, subtract.get(0).getMin(), DELTA);
    assertEquals(10, subtract.get(0).getMax(), DELTA);
  }

  @Test
  public void testInnerIntersection() {
    Range range = new Range(0, 10);
    Range intersection = range.getIntersection(new Range(3, 5));
    assertEquals(3, intersection.getMin(), DELTA);
    assertEquals(5, intersection.getMax(), DELTA);
  }

  @Test
  public void testLeftIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(-3, 5));
    assertEquals(1, intersection.getMin(), DELTA);
    assertEquals(5, intersection.getMax(), DELTA);
  }

  @Test
  public void testRightIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(5, 15));
    assertEquals(5, intersection.getMin(), DELTA);
    assertEquals(10, intersection.getMax(), DELTA);
  }

  @Test
  public void testNoIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(15, 25));
    assertTrue(intersection.isEmpty());
  }

  @Test
  public void testPointIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(5, 5));
    assertEquals(5, intersection.getMin(), DELTA);
    assertEquals(5, intersection.getMax(), DELTA);
  }

  @Test
  public void testIntersectionPoint() {
    Range range = new Range(5, 5);
    Range intersection = range.getIntersection(new Range(0, 10));
    assertEquals(5, intersection.getMin(), DELTA);
    assertEquals(5, intersection.getMax(), DELTA);
  }

  @Test
  public void testMinMax() {
    Range range = new Range(0, 5);
    assertEquals(0, range.getMin(), DELTA);
    assertEquals(5, range.getMax(), DELTA);

    range.setMin(3);
    assertEquals(3, range.getMin(), DELTA);
    range.setMax(20);
    assertEquals(20, range.getMax(), DELTA);

    range.set(8, 15);
    assertEquals(8, range.getMin(), DELTA);
    assertEquals(15, range.getMax(), DELTA);

    range.set(new Range(10, 30));
    assertEquals(10, range.getMin(), DELTA);
    assertEquals(30, range.getMax(), DELTA);

    assertEquals(20, range.getLength(), DELTA);
  }

  @Test
  public void testClamp() {
    Range range = new Range(20, 100);

    // Clamp to max
    assertEquals(range.clamp(140), 100, DELTA);

    // Clamp to min
    assertEquals(range.clamp(10), 20, DELTA);
  }

  @Test
  public void testShift() {
    Range range = new Range(0, 100);
    assertEquals(0, range.getMin(), DELTA);
    assertEquals(100, range.getMax(), DELTA);

    range.shift(50);
    assertEquals(50, range.getMin(), DELTA);
    assertEquals(150, range.getMax(), DELTA);

    range.shift(-100);
    assertEquals(-50, range.getMin(), DELTA);
    assertEquals(50, range.getMax(), DELTA);
  }

  @Test
  public void testExpand() {
    Range range = new Range(0, 100);
    assertEquals(0, range.getMin(), DELTA);
    assertEquals(100, range.getMax(), DELTA);

    range.expand(5, 30);
    assertEquals(0, range.getMin(), DELTA);
    assertEquals(100, range.getMax(), DELTA);

    range.expand(-20, 30);
    assertEquals(-20, range.getMin(), DELTA);
    assertEquals(100, range.getMax(), DELTA);

    range.expand(0, 200);
    assertEquals(-20, range.getMin(), DELTA);
    assertEquals(200, range.getMax(), DELTA);

    range.expand(-40, 250);
    assertEquals(-40, range.getMin(), DELTA);
    assertEquals(250, range.getMax(), DELTA);
  }

  @Test
  public void testContains() {
    Range range = new Range(0, 100);
    assertTrue(range.contains(0));
    assertTrue(range.contains(100));
    assertTrue(range.contains(50));
    assertFalse(range.contains(-0.5));
    assertFalse(range.contains(100.5));
  }

  @Test
  public void testToString() {
    Range same1 = new Range(0, 100);
    Range same2 = new Range(0, 100);
    Range different = new Range(1, 99);
    Range r = new Range(5, 10);
    Range empty = new Range();

    assertTrue(same1.toString().equals(same2.toString()));
    assertFalse(same1.toString().equals(different.toString()));

    assertFalse(r.toString().equals(empty.toString()));
    r.clear();
    assertTrue(r.toString().equals(empty.toString()));
  }
}
