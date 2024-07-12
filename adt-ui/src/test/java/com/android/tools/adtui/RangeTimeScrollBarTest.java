/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.Range;
import com.intellij.testFramework.junit5.TestApplication;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@TestApplication
public class RangeTimeScrollBarTest {
  private static final double EPSILON = 1e-4;

  @Test
  public void testInitialization() {
    Range global = new Range(secToUs(0), secToUs(10));
    Range view = new Range(secToUs(2), secToUs(5));
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.MICROSECONDS);

    assertThat(scrollBar.getMinimum()).isEqualTo(0);
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(10));
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(2));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(5 - 2));
  }

  @Test
  public void testRangeChanged() {
    Range global = new Range(secToUs(0), secToUs(10));
    Range view = new Range(secToUs(2), secToUs(5));
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.MICROSECONDS);

    // view range changed
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(2));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(5 - 2));

    view.set(secToUs(3), secToUs(8));

    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(3));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(8 - 3));

    // global range changed
    assertThat(scrollBar.getMinimum()).isEqualTo(secToMillis(0));
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(10));

    global.set(secToUs(0), secToUs(15));

    assertThat(scrollBar.getMinimum()).isEqualTo(secToMillis(0));
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(15));
  }

  @Test
  public void testScrollBarValueChanged() {
    Range global = new Range(secToUs(0), secToUs(10));
    Range view = new Range(secToUs(2), secToUs(5));
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.MICROSECONDS);

    // change value
    assertThat(view.getMin()).isWithin(EPSILON).of(secToUs(2));
    assertThat(view.getMax()).isWithin(EPSILON).of(secToUs(5));
    scrollBar.setValue((int)secToMillis(3));
    assertThat(view.getMin()).isWithin(EPSILON).of(secToUs(3));
    assertThat(view.getMax()).isWithin(EPSILON).of(secToUs(6));
  }

  @Test
  public void shouldWorkWithDifferentTimeUnits() {
    // Seconds
    Range global = new Range(0, 10);
    Range view = new Range(2, 5);
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.SECONDS);

    assertThat(scrollBar.getMinimum()).isEqualTo(0);
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(10));
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(2));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(5 - 2));

    // Nanoseconds
    global = new Range(TimeUnit.SECONDS.toNanos(0), TimeUnit.SECONDS.toNanos(11));
    view = new Range(TimeUnit.SECONDS.toNanos(3), TimeUnit.SECONDS.toNanos(6));
    scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.NANOSECONDS);

    assertThat(scrollBar.getMinimum()).isEqualTo(0);
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(11));
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(3));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(6 - 3));
  }

  @Test
  public void valuesShouldBeRelativeToMinOfGlobalRange() {
    Range global = new Range(secToUs(5), secToUs(15));
    Range view = new Range(secToUs(6), secToUs(14));
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.MICROSECONDS);

    assertThat(scrollBar.getMinimum()).isEqualTo(0);
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(15 - 5));
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(6 - 5));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(14 - 6));
  }

  @Test
  public void viewRangePartiallyOutsideOfGlobalRange() {
    Range global = new Range(secToUs(2), secToUs(12));
    Range view = new Range(secToUs(0), secToUs(10));
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.MICROSECONDS);

    assertThat(scrollBar.getMinimum()).isEqualTo(0);
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(10));
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(0));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(8));
  }

  @Test
  public void viewRangeOutsideToTheLeftOfGlobalRange() {
    Range global = new Range(secToUs(10), secToUs(22));
    Range view = new Range(secToUs(0), secToUs(5));
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.MICROSECONDS);

    assertThat(scrollBar.getMinimum()).isEqualTo(0);
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(12));
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(0));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(0));
  }

  @Test
  public void viewRangeOutsideToTheRightOfGlobalRange() {
    Range global = new Range(secToUs(10), secToUs(22));
    Range view = new Range(secToUs(25), secToUs(30));
    RangeTimeScrollBar scrollBar = new RangeTimeScrollBar(global, view, TimeUnit.MICROSECONDS);

    assertThat(scrollBar.getMinimum()).isEqualTo(0);
    assertThat(scrollBar.getMaximum()).isEqualTo(secToMillis(12));
    assertThat(scrollBar.getValue()).isEqualTo(secToMillis(12));
    assertThat(scrollBar.getVisibleAmount()).isEqualTo(secToMillis(0));
  }

  private static long secToUs(long sec) {
    return TimeUnit.SECONDS.toMicros(sec);
  }

  private static long secToMillis(long sec) {
    return TimeUnit.SECONDS.toMillis(sec);
  }
}