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

import com.android.tools.adtui.model.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.MockAxisFormatter;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.*;

public class AxisComponentTest {

  @Test
  public void testRangeMinAndMax() {
    AxisComponentModel model = new AxisComponentModel(new Range(10, 50), new MockAxisFormatter(1, 1, 1));
    model.setGlobalRange(new Range(0, 100));
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.RIGHT);
    component.setShowMin(true);
    component.setShowMax(true);
    component.render();
    assertEquals("1cm", component.getMinLabel());
    assertEquals("5cm", component.getMaxLabel());
  }

  @Test
  public void testSizeForHorizontalOrientation() {
    AxisComponentModel model = new AxisComponentModel(new Range(0, 100), new MockAxisFormatter(1, 1, 1));
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.RIGHT);
    component.setMarkerLengths(5, 5);
    Dimension dimension = component.getPreferredSize();
    assertTrue(dimension.getWidth() > dimension.getHeight());
  }

  @Test
  public void testSizeForVerticalOrientation() {
    AxisComponentModel model = new AxisComponentModel(new Range(0, 100), new MockAxisFormatter(1, 1, 1));
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.TOP);
    component.setMarkerLengths(5, 5);
    Dimension dimension = component.getPreferredSize();
    assertFalse(dimension.getWidth() > dimension.getHeight());
  }
}
