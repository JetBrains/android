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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.MockAxisFormatter;
import org.junit.Test;
import org.mockito.Mockito;

import java.awt.*;

import static com.android.tools.adtui.AxisComponent.REMOVE_MAJOR_TICK_DENSITY;
import static org.junit.Assert.*;

public class AxisComponentTest {
  @Test
  public void testRangeMinAndMax() {
    AxisComponentModel model =
      new ResizingAxisComponentModel.Builder(new Range(10, 50), new MockAxisFormatter(1, 1, 1)).setGlobalRange(new Range(0, 100)).build();
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.RIGHT, true);
    component.setShowMin(true);
    component.setShowMax(true);
    component.calculateMarkers(new Dimension(100, 100));
    assertEquals("1cm", component.getMinLabel());
    assertEquals("5cm", component.getMaxLabel());
  }

  @Test
  public void testSizeForHorizontalOrientation() {
    AxisComponentModel model = new ResizingAxisComponentModel.Builder(new Range(0, 100), new MockAxisFormatter(1, 1, 1)).build();
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.RIGHT, true);
    component.setMarkerLengths(5, 5);
    Dimension dimension = component.getPreferredSize();
    assertTrue(dimension.getWidth() > dimension.getHeight());
  }

  @Test
  public void testSizeForVerticalOrientation() {
    AxisComponentModel model = new ResizingAxisComponentModel.Builder(new Range(0, 100), new MockAxisFormatter(1, 1, 1)).build();
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.TOP, true);
    component.setMarkerLengths(5, 5);
    Dimension dimension = component.getPreferredSize();
    assertFalse(dimension.getWidth() > dimension.getHeight());
  }

  @Test
  public void testSmallTimelines() {
    final int MAJOR_TICKS = 5;
    AxisComponentModel model = new ResizingAxisComponentModel.Builder(new Range(10, 50), new MockAxisFormatter(1, MAJOR_TICKS - 1, 1))
      .setGlobalRange(new Range(0, 100)).build();
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.RIGHT, true);
    FontMetrics fontMetrics = component.getFontMetrics(AdtUiUtils.DEFAULT_FONT);
    component.calculateMarkers(new Dimension(100, (MAJOR_TICKS * (fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent()) * 3 + 1) / 2));
    assertTrue(component.getMarkerLabelDensity() >= REMOVE_MAJOR_TICK_DENSITY);
    component.calculateMarkers(new Dimension(100, MAJOR_TICKS * (fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent())));
    assertTrue(component.getMarkerLabelDensity() < REMOVE_MAJOR_TICK_DENSITY);
  }

  @Test
  public void testInitialMarkers() {
    final int MAJOR_TICKS = 5;
    AxisComponentModel model = new ResizingAxisComponentModel.Builder(new Range(10, 50), new MockAxisFormatter(1, MAJOR_TICKS - 1, 1))
      .setGlobalRange(new Range(0, 100)).build();
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.RIGHT, true);
    component.setShowMin(true);
    component.setShowMax(true);
    // Call draw instead of calculateMarkers to check whether there are initial markers.
    Graphics2D fakeGraphics = Mockito.mock(Graphics2D.class);
    component.draw(fakeGraphics, new Dimension(100, 100));
    assertEquals("1cm", component.getMinLabel());
    assertEquals("5cm", component.getMaxLabel());
  }

  @Test
  public void testLabelOutOfMarkersRange() {
    final int MAJOR_TICKS = 5;
    AxisComponentModel model =
      new ResizingAxisComponentModel.Builder(new Range(10, 90), new MockAxisFormatter(1, MAJOR_TICKS - 1, 1))
        .setGlobalRange(new Range(0, 100)).setMarkerRange(new Range(0, 50)).build();
    AxisComponent component = new AxisComponent(model, AxisComponent.AxisOrientation.TOP, true);
    component.setShowMin(true);
    component.setShowMax(true);
    component.calculateMarkers(new Dimension(100, 100));
    assertEquals("1cm", component.getMinLabel());
    assertNull(component.getMaxLabel());
  }
}
