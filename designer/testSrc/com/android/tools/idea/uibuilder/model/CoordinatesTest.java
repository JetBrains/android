/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.tools.idea.uibuilder.surface.ScreenView;
import junit.framework.TestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoordinatesTest extends TestCase {
  private static ScreenView createScreenView(double scale, @SwingCoordinate int x, @SwingCoordinate int y) {
    ScreenView screenView = mock(ScreenView.class);
    when(screenView.getScale()).thenReturn(scale);
    when(screenView.getX()).thenReturn(x);
    when(screenView.getY()).thenReturn(y);

    return screenView;
  }

  public void testAndroidToSwing() {
    ScreenView screenView = createScreenView(0.5, 100, 110);
    assertEquals(100, Coordinates.getSwingX(screenView, 0));
    assertEquals(110, Coordinates.getSwingY(screenView, 0));

    assertEquals(100 + 500, Coordinates.getSwingX(screenView, 1000));
    assertEquals(110 + 500, Coordinates.getSwingY(screenView, 1000));
    assertEquals(500, Coordinates.getSwingDimension(screenView, 1000));
  }

  public void testSwingToAndroid() {
    ScreenView screenView = createScreenView(0.5, 100, 110);
    assertEquals(0, Coordinates.getAndroidX(screenView, 100));
    assertEquals(0, Coordinates.getAndroidY(screenView, 110));

    assertEquals(1000, Coordinates.getAndroidX(screenView, 100 + 500));
    assertEquals(1000, Coordinates.getAndroidY(screenView, 110 + 500));
    assertEquals(1000, Coordinates.getAndroidDimension(screenView, 500));
  }
}