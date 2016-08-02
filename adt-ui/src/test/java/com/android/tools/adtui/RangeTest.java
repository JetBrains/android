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

import com.android.annotations.NonNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class RangeTest {

  private static float THRESHOLD = 0.001f;

  @Test
  public void testSetMin() throws Exception {
    Range range = new Range(0f, 100f);
    range.setLerpFraction(1f);

    // Assert initial min value.
    assertThat(range.getMin()).isWithin(THRESHOLD).of(0f);

    // Immediate set.
    range.setMin(5f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(5f);

    // Immediate set and lock, no further values can be set until next cycle.
    range.setMin(10f);
    range.lockValues();
    assertThat(range.getMin()).isWithin(THRESHOLD).of(10f);

    range.setMin(15f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(10f);

    // Animate and reset to make sure values can be set again.
    triggerUpdate(range);
    range.setMin(20f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(20f);
  }

  @Test
  public void testSetMinTarget() throws Exception {
    Range range = new Range(0f, 100f);
    range.setLerpFraction(1f);

    // Assert initial min value.
    assertThat(range.getMin()).isWithin(THRESHOLD).of(0f);

    // Interpolate set.
    range.setMinTarget(5f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(0f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(5f);

    // Interpolate set and lock, no further values can be set until next cycle.
    range.setMinTarget(10f);
    range.lockValues();
    range.setMinTarget(20f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(10f);

    // Animate and reset to make sure values can be set again.
    range.setMinTarget(30f);
    range.setMinTarget(40f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(40f);

  }

  @Test
  public void testSetMax() throws Exception {
    Range range = new Range(0f, 100f);
    range.setLerpFraction(1f);

    // Assert initial min value.
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    // Immediate set.
    range.setMax(90f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(90f);

    // Immediate set and lock, no further values can be set until next cycle.
    range.setMax(80f);
    range.lockValues();
    assertThat(range.getMax()).isWithin(THRESHOLD).of(80f);

    range.setMax(70f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(80f);

    // Animate and reset to make sure values can be set again.
    triggerUpdate(range);
    range.setMax(60f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(60f);

  }

  @Test
  public void testSetMaxTarget() throws Exception {
    Range range = new Range(0f, 100f);
    range.setLerpFraction(1f);

    // Assert initial min value.
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    // Interpolate set.
    range.setMaxTarget(90f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);
    triggerUpdate(range);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(90f);

    // Interpolate set and lock, no further values can be set until next cycle.
    range.setMaxTarget(80f);
    range.lockValues();
    range.setMaxTarget(70f);
    triggerUpdate(range);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(80f);

    // Animate and reset to make sure values can be set again.
    range.setMaxTarget(60f);
    range.setMaxTarget(50f);
    triggerUpdate(range);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(50f);
  }

  @Test
  public void testSet() throws Exception {
    Range range = new Range();
    range.setLerpFraction(1f);

    // Immediate set.
    range.set(50f, 100f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(50f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    // lock and set, values should not be changed
    range.lockValues();
    range.set(70f, 80f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(50f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    // Animate and reset to make sure values can be set again.
    triggerUpdate(range);
    range.set(20f, 30f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(20f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(30f);
  }

  @Test
  public void testSetTarget() throws Exception {
    Range range = new Range();
    range.setLerpFraction(1f);

    // Interpolate set.
    range.setTarget(50f, 100f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(0f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(0f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(50f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    // lock and set, values should not be changed
    range.lockValues();
    range.setTarget(70f, 80f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(50f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(100f);

    // Make sure values can be set again after last update.
    range.setTarget(20f, 30f);
    triggerUpdate(range);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(20f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(30f);
  }

  @Test
  public void testClamp() throws Exception {
    Range range = new Range(0f, 100f);

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
    Range range = new Range(0f, 100f);

    // Shift forward.
    range.shift(10f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(10f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(110f);

    // Shift backward.
    range.shift(-20f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(-10f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(90f);

    // Lock and shift - no values should be changed.
    range.lockValues();
    range.shift(-20f);
    assertThat(range.getMin()).isWithin(THRESHOLD).of(-10f);
    assertThat(range.getMax()).isWithin(THRESHOLD).of(90f);
  }

  private static void triggerUpdate(@NonNull Range range) {
    range.animate(1);
    range.postAnimate();
  }
}