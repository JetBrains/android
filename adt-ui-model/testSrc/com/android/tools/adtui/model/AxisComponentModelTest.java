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

import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.updater.Updater;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AxisComponentModelTest {


  @Test
  public void testClampToMajorTickOnFirstUpdate() throws Exception {
    // Test that during the first update, if the AxisComponent is set to clamp to the next major tick,
    // The range will be immediately snapped to the major tick value instead of going through interpolation.
    // Subsequent updates will interpolate to the major tick.

    // Setting the minimum tick value to 10, so that a Range of {0,5} should adjust to {0,10} by the axis.
    SingleUnitAxisFormatter formatter = new SingleUnitAxisFormatter(1, 1, 10, "");
    Range range = new Range(0, 5);
    FakeTimer t = new FakeTimer();
    Updater choreographer = new Updater(t);

    AxisComponentModel model = new AxisComponentModel(range, formatter);
    model.setClampToMajorTicks(true);
    choreographer.register(model);

    assertEquals(model.getRange().getMax(), 5.0, 0.0);  // before update.
    t.step();
    assertEquals(model.getRange().getMax(), 10.0, 0.0);  // after update.
  }

  @Test
  public void testRangeIsNotChangedWhenNoNeedToClampToMajorTick() {
    SingleUnitAxisFormatter formatter = new SingleUnitAxisFormatter(1, 1, 10, "");
    Range range = new Range(0, 5);
    FakeTimer t = new FakeTimer();
    Updater choreographer = new Updater(t);

    AxisComponentModel model = new AxisComponentModel(range, formatter);
    model.setClampToMajorTicks(false);
    choreographer.register(model);

    assertEquals(model.getRange().getMax(), 5.0, 0.0);  // before update.
    t.step();
    assertEquals(model.getRange().getMax(), 5.0, 0.0);  // after update.
  }
}