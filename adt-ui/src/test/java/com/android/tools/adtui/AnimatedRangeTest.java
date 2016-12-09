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

package com.android.tools.adtui;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class AnimatedRangeTest {

  private static float THRESHOLD = 0.001f;

  @Test
  public void testSetMin() throws Exception {
    AnimatedRange range = new AnimatedRange(0f, 100f);
    range.setLerpFraction(1f);

    // Assert initial min value.
    assertThat(range.getMin()).isWithin(THRESHOLD).of(0f);

    range.setMin(5f);
    // Min should not have changed until animate.
    assertThat(range.getMin()).isWithin(THRESHOLD).of(0);

    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(5f);
  }

  @Test
  public void testSetMax() throws Exception {
    AnimatedRange range = new AnimatedRange(0f, 100f);
    range.setLerpFraction(1f);

    // Assert initial min value.
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    range.setMax(90f);
    // Max should not have changed until animate.
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    triggerUpdate(range);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(90f);
  }

  @Test
  public void testSet() throws Exception {
    AnimatedRange range = new AnimatedRange();
    range.setLerpFraction(1f);

    range.set(50f, 100f);
    // Min/Max should not have changed until animate.
    assertThat(range.getMin()).isWithin(THRESHOLD).of(0);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(0);

    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(50f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);
  }

  @Test
  public void testClamp() throws Exception {
    AnimatedRange range = new AnimatedRange(0f, 100f);
    range.setLerpFraction(1f);

    // No clamping should be applied within range.
    double value = range.clamp(70f);
    assertThat(value).isWithin(THRESHOLD).of(70f);

    // Clamp to min.
    value = range.clamp(-1f);
    assertThat(value).isWithin(THRESHOLD).of(0f);

    // Clamp to max.
    value = range.clamp(101f);
    assertThat(value).isWithin(THRESHOLD).of(100f);
  }

  @Test
  public void testShift() throws Exception {
    AnimatedRange range = new AnimatedRange(0f, 100f);
    range.setLerpFraction(1f);

    // Shift forward.
    range.shift(10f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(10f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(110f);

    // Shift backward.
    range.shift(-20f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(-10f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(90f);
  }

  private static void triggerUpdate(@NotNull AnimatedRange range) {
    range.update(1);
    range.postUpdate();
  }
}