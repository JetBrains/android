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
import junit.framework.TestCase;

public class RangeTest extends TestCase {

  private static float THRESHOLD = 0.001f;

  public void testSetMin() throws Exception {
    Range range = new Range(0f, 100f);
    range.setInterpolationFraction(1f);

    // Assert initial min value.
    assertEquals(0f, range.getMin(), THRESHOLD);

    // Immediate set.
    range.setMin(5f);
    assertEquals(5f, range.getMin(), THRESHOLD);

    // Immediate set and lock, no further values can be set until next cycle.
    range.setMin(10f);
    range.lockValues();
    assertEquals(10f, range.getMin(), THRESHOLD);

    range.setMin(15f);
    assertEquals(10f, range.getMin(), THRESHOLD);

    // Animate and reset to make sure values can be set again.
    triggerUpdate(range);
    range.setMin(20f);
    assertEquals(20f, range.getMin(), THRESHOLD);
  }

  public void testSetMinTarget() throws Exception {
    Range range = new Range(0f, 100f);
    range.setInterpolationFraction(1f);

    // Assert initial min value.
    assertEquals(0f, range.getMin(), THRESHOLD);

    // Interpolate set.
    range.setMinTarget(5f);
    assertEquals(0f, range.getMin(), THRESHOLD);
    triggerUpdate(range);
    assertEquals(5f, range.getMin(), THRESHOLD);

    // Interpolate set and lock, no further values can be set until next cycle.
    range.setMinTarget(10f);
    range.lockValues();
    range.setMinTarget(20f);
    triggerUpdate(range);
    assertEquals(10f, range.getMin(), THRESHOLD);

    // Animate and reset to make sure values can be set again.
    range.setMinTarget(30f);
    range.setMinTarget(40f);
    triggerUpdate(range);
    assertEquals(40f, range.getMin(), THRESHOLD);

  }

  public void testSetMax() throws Exception {
    Range range = new Range(0f, 100f);
    range.setInterpolationFraction(1f);

    // Assert initial min value.
    assertEquals(100f, range.getMax(), THRESHOLD);

    // Immediate set.
    range.setMax(90f);
    assertEquals(90f, range.getMax(), THRESHOLD);

    // Immediate set and lock, no further values can be set until next cycle.
    range.setMax(80f);
    range.lockValues();
    assertEquals(80f, range.getMax(), THRESHOLD);

    range.setMax(70f);
    assertEquals(80f, range.getMax(), THRESHOLD);

    // Animate and reset to make sure values can be set again.
    triggerUpdate(range);
    range.setMax(60f);
    assertEquals(60f, range.getMax(), THRESHOLD);

  }

  public void testSetMaxTarget() throws Exception {
    Range range = new Range(0f, 100f);
    range.setInterpolationFraction(1f);

    // Assert initial min value.
    assertEquals(100f, range.getMax(), THRESHOLD);

    // Interpolate set.
    range.setMaxTarget(90f);
    assertEquals(100f, range.getMax(), THRESHOLD);
    triggerUpdate(range);
    assertEquals(90f, range.getMax(), THRESHOLD);

    // Interpolate set and lock, no further values can be set until next cycle.
    range.setMaxTarget(80f);
    range.lockValues();
    range.setMaxTarget(70f);
    triggerUpdate(range);
    assertEquals(80f, range.getMax(), THRESHOLD);

    // Animate and reset to make sure values can be set again.
    range.setMaxTarget(60f);
    range.setMaxTarget(50f);
    triggerUpdate(range);
    assertEquals(50f, range.getMax(), THRESHOLD);
  }

  private static void triggerUpdate(@NonNull Range range) {
    range.animate(1);
    range.postAnimate();
  }
}