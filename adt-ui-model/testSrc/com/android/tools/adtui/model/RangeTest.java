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

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.List;
import kotlin.Pair;
import org.junit.Test;

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
  public void testIntersectsWith() {
    // Inner intersection
    assertThat(new Range(1, 10).intersectsWith(new Range(3, 5))).isTrue();
    // Outer intersection
    assertThat(new Range(3, 5).intersectsWith(new Range(1, 10))).isTrue();
    // Left intersection
    assertThat(new Range(1, 10).intersectsWith(new Range(-3, 5))).isTrue();
    // Right intersection
    assertThat(new Range(1, 10).intersectsWith(new Range(5, 15))).isTrue();
    // Point intersection
    assertThat(new Range(1, 10).intersectsWith(new Range(5, 5))).isTrue();
    assertThat(new Range(5, 5).intersectsWith(new Range(1, 10))).isTrue();
    assertThat(new Range(1, 10).intersectsWith(new Range(10, 20))).isTrue();
    // No intersection
    assertThat(new Range(1, 10).intersectsWith(new Range(15, 25))).isFalse();
    // Intersection with [min, max]
    assertThat(new Range(3, 5).intersectsWith(1, 10)).isTrue();
  }

  @Test
  public void testIntersectionLength() {
    Range range = new Range(0, 10);
    // Inner intersection
    assertThat(range.getIntersectionLength(2, 3)).isWithin(DELTA).of(1);
    // Left intersection
    assertThat(range.getIntersectionLength(-1, 1)).isWithin(DELTA).of(1);
    // Right intersection
    assertThat(range.getIntersectionLength(9, 11)).isWithin(DELTA).of(1);
    // Point intersection
    assertThat(range.getIntersectionLength(10, 20)).isEqualTo(0.0);
    // No intersection
    assertThat(range.getIntersectionLength(15, 20)).isEqualTo(0.0);
  }

  @Test
  public void testIntersectionLengthWithRange() {
    Range range = new Range(0, 10);
    assertThat(range.getIntersectionLength(new Range(2, 3))).isWithin(DELTA).of(1);
    assertThat(range.getIntersectionLength(new Range(-1, 1))).isWithin(DELTA).of(1);
    assertThat(range.getIntersectionLength(new Range(9, 11))).isWithin(DELTA).of(1);
    assertThat(range.getIntersectionLength(new Range(10, 20))).isEqualTo(0.0);
    assertThat(range.getIntersectionLength(new Range(15, 20))).isEqualTo(0.0);
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

  // Currently, the Range class is used as a key in at least one hashmap, so if we override the
  // class's equals method, we'd break it. We should fix this eventually, but carefully! And during
  // a development window that's not too risky.
  @Test
  public void assertRangeEqualityIntentionallyNotOverwridden() {
    // TODO(b/79753868): Although we do want to override it later...
    Range r1 = new Range(123, 456);
    Range r2 = new Range(123, 456);

    assertThat(r1).isNotEqualTo(r2);
  }

  @Test
  public void testIsSameAs() {
    Range same1 = new Range(0, 100);
    Range same2 = new Range(0, 100);
    Range same3 = new Range(same2);
    Range different = new Range(1, 99);
    Range rangeToClear = new Range(5, 10);
    Range empty1 = new Range();
    Range empty2 = new Range(10, -10);

    assertThat(same1.isSameAs(same2)).isTrue();
    assertThat(same1.isSameAs(same3)).isTrue();
    assertThat(same1.isSameAs(different)).isFalse();

    assertThat(rangeToClear.isSameAs(empty1)).isFalse();
    rangeToClear.clear();
    assertThat(rangeToClear.isSameAs(empty1)).isTrue();
    assertThat(empty2.isSameAs(empty1)).isTrue();
  }


  @Test
  public void rangeAdjustmentCoversDesiredBounds() {
    List<Pair<Double, Double>>
      ranges = Arrays.asList(new Pair<>(0.0, 1.0),
                             new Pair<>(8.0, 9.0),
                             new Pair<>(0.0, 4.7),
                             new Pair<>(4.7, 9.0)),
      selections = Arrays.asList(new Pair<>(2.0, 5.0),
                                 new Pair<>(4.0, 5.0),
                                 new Pair<>(4.5, 5.0));
    ranges.forEach(range ->
      selections.forEach(selection -> {
          Range r = new Range(range.getFirst(), range.getSecond());
          r.adjustToContain(selection.getFirst(), selection.getSecond());
          assertThat(r.contains(selection.getFirst())).isTrue();
          assertThat(r.contains(selection.getSecond())).isTrue();
      }));
  }
}
