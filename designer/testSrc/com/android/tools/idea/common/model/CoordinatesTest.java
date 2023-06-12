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
package com.android.tools.idea.common.model;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.testutils.ImageDiffUtil;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import junit.framework.TestCase;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoordinatesTest extends TestCase {
  private static ScreenView createScreenView(double scale, @SwingCoordinate int x, @SwingCoordinate int y) {
    ScreenView screenView = mock(ScreenView.class);
    when(screenView.getScale()).thenReturn(scale);
    when(screenView.getX()).thenReturn(x);
    when(screenView.getY()).thenReturn(y);
    LayoutlibSceneManager sceneManager = mock(LayoutlibSceneManager.class);
    when(screenView.getSceneManager()).thenReturn(sceneManager);
    when(sceneManager.getSceneScalingFactor()).thenReturn(2.0f);

    return screenView;
  }

  public void testAndroidToSwing() {
    ScreenView screenView = createScreenView(0.5, 100, 110);
    assertEquals(100, Coordinates.getSwingX(screenView, 0));
    assertEquals(110, Coordinates.getSwingY(screenView, 0));

    if (StudioFlags.NELE_DP_SIZED_PREVIEW.get()) {
      assertEquals(100 + 250, Coordinates.getSwingX(screenView, 1000));
      assertEquals(110 + 250, Coordinates.getSwingY(screenView, 1000));
      assertEquals(250, Coordinates.getSwingDimension(screenView, 1000));
    }
    else {
      assertEquals(100 + 500, Coordinates.getSwingX(screenView, 1000));
      assertEquals(110 + 500, Coordinates.getSwingY(screenView, 1000));
      assertEquals(500, Coordinates.getSwingDimension(screenView, 1000));
    }
  }

  public void testSwingToAndroid() {
    ScreenView screenView = createScreenView(0.5, 100, 110);
    assertEquals(0, Coordinates.getAndroidX(screenView, 100));
    assertEquals(0, Coordinates.getAndroidY(screenView, 110));

    if (StudioFlags.NELE_DP_SIZED_PREVIEW.get()) {
      assertEquals(1000, Coordinates.getAndroidX(screenView, 100 + 250));
      assertEquals(1000, Coordinates.getAndroidY(screenView, 110 + 250));
      assertEquals(1000, Coordinates.getAndroidDimension(screenView, 250));
    }
    else {
      assertEquals(1000, Coordinates.getAndroidX(screenView, 100 + 500));
      assertEquals(1000, Coordinates.getAndroidY(screenView, 110 + 500));
      assertEquals(1000, Coordinates.getAndroidDimension(screenView, 500));
    }
  }

  public void testGraphicsTransform() throws IOException {
    ScreenView screenView = createScreenView(0.5, 10, 10);
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = ((Graphics2D)image.getGraphics());
    Coordinates.transformGraphics(screenView, graphics);
    graphics.fillRect(0, 0, 50, 50);
    graphics.dispose();

    BufferedImage golden = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics goldenGraphics = golden.getGraphics();
    goldenGraphics.fillRect(10, 10, 25, 25);
    goldenGraphics.dispose();

    ImageDiffUtil.assertImageSimilar("TransformedGraphics", golden, image, 0.0);
  }
}