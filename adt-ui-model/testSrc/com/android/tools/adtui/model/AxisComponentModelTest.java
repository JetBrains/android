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

import static org.junit.Assert.assertEquals;

import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import org.junit.Test;

public class AxisComponentModelTest {

  @Test
  public void testClampToMajorTickOnConstructionAndReset() {
    // Test that upon construction/reset, if the AxisComponent is set to clamp to the next major tick,
    // the range will be immediately snapped to the major tick value instead of going through interpolation.
    // Subsequent updates will interpolate to the major tick.

    // Setting the minimum tick value to 10, so that a Range of {0,5} should adjust to {0,10} by the axis.
    SingleUnitAxisFormatter formatter = new SingleUnitAxisFormatter(1, 1, 10, "");
    Range range = new Range(0, 5);

    AxisComponentModel model = new ClampedAxisComponentModel.Builder(range, formatter).build();
    assertEquals(5.0, model.getRange().getMax(), 0.0); // Not updating range until first update.
    model.getRange().setMax(5.0);
    model.reset();
    assertEquals(10.0, model.getRange().getMax(), 0.0);
  }
}