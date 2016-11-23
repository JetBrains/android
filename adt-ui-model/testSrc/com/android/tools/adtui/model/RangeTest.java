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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class RangeTest {

  @Test
  public void testSubstractAll() {
    Range range = new Range(0, 1);
    List<Range> substract = range.subtract(new Range(-10, 10));
    assertThat(substract, empty());
  }

  @Test
  public void testSubstractInner() {
    Range range = new Range(0, 10);
    List<Range> substract = range.subtract(new Range(2, 5));
    assertThat(substract, hasSize(2));
    assertEquals(0, substract.get(0).getMin(), 0);
    assertEquals(2, substract.get(0).getMax(), 0);
    assertEquals(5, substract.get(1).getMin(), 0);
    assertEquals(10, substract.get(1).getMax(), 0);
  }

  @Test
  public void testSubstractLeft() {
    Range range = new Range(0, 10);
    List<Range> substract = range.subtract(new Range(-5, 5));
    assertThat(substract, hasSize(1));
    assertEquals(5, substract.get(0).getMin(), 0);
    assertEquals(10, substract.get(0).getMax(), 0);
  }

  @Test
  public void testSubstractRight() {
    Range range = new Range(0, 10);
    List<Range> substract = range.subtract(new Range(5, 15));
    assertThat(substract, hasSize(1));
    assertEquals(0, substract.get(0).getMin(), 0);
    assertEquals(5, substract.get(0).getMax(), 0);
  }

  @Test
  public void testSubstractNone() {
    Range range = new Range(0, 10);
    List<Range> substract = range.subtract(new Range(15, 25));
    assertThat(substract, hasSize(1));
    assertEquals(0, substract.get(0).getMin(), 0);
    assertEquals(10, substract.get(0).getMax(), 0);
  }

  @Test
  public void testSubstractEmpty() {
    Range range = new Range(0, 10);
    List<Range> substract = range.subtract(new Range(5, 5));
    assertThat(substract, hasSize(1));
    assertEquals(0, substract.get(0).getMin(), 0);
    assertEquals(10, substract.get(0).getMax(), 0);
  }

  @Test
  public void testSubstractFromEmpty() {
    Range range = new Range(0, 0);
    List<Range> substract = range.subtract(new Range(-5, 5));
    assertThat(substract, empty());
  }

  @Test
  public void testInnerIntersection() {
    Range range = new Range(0, 10);
    Range intersection = range.getIntersection(new Range(3, 5));
    assertEquals(3, intersection.getMin(), 0);
    assertEquals(5, intersection.getMax(), 0);
  }

  @Test
  public void testLeftIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(-3, 5));
    assertEquals(1, intersection.getMin(), 0);
    assertEquals(5, intersection.getMax(), 0);
  }

  @Test
  public void testRightIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(5, 15));
    assertEquals(5, intersection.getMin(), 0);
    assertEquals(10, intersection.getMax(), 0);
  }

  @Test
  public void testNoIntersection() {
    Range range = new Range(1, 10);
    Range intersection = range.getIntersection(new Range(15, 25));
    assertEquals(0, intersection.getMin(), 0);
    assertEquals(0, intersection.getMax(), 0);
  }
}
