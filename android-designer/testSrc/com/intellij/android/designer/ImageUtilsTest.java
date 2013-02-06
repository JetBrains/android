/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer;

import junit.framework.TestCase;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageUtilsTest extends TestCase {
  public void testScaleImage() throws Exception {
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
    Graphics g = image.getGraphics();
    g.setColor(new Color(0xFF00FF00, true));
    g.fillRect(0, 0, image.getWidth(), image.getHeight());
    g.setColor(new Color(0xFFFF0000, true));
    g.fillRect(25, 25, 50, 50);
    g.dispose();

    BufferedImage scaled = ImageUtils.scale(image, 0.5, 0.5);
    assertEquals(50, scaled.getWidth());
    assertEquals(50, scaled.getHeight());
    assertEquals(0xFF00FF00, scaled.getRGB(0, 0));
    assertEquals(0xFF00FF00, scaled.getRGB(49, 49));
    assertEquals(0xFFFF0000, scaled.getRGB(25, 25));

    scaled = ImageUtils.scale(image, 2.0, 2.0);
    assertEquals(200, scaled.getWidth());
    assertEquals(200, scaled.getHeight());
    assertEquals(0xFF00FF00, scaled.getRGB(0, 0));
    assertEquals(0xFF00FF00, scaled.getRGB(48, 48));
    assertEquals(0xFFFF0000, scaled.getRGB(100, 100));
    assertEquals(0xFF00FF00, scaled.getRGB(199, 199));

    scaled = ImageUtils.scale(image, 0.25, 0.25);
    assertEquals(25, scaled.getWidth());
    assertEquals(25, scaled.getHeight());
    assertEquals(0xFF00FF00, scaled.getRGB(0, 0));
    assertEquals(0xFF00FF00, scaled.getRGB(24, 24));
    assertEquals(0xFFFF0000, scaled.getRGB(13, 13));

    scaled = ImageUtils.scale(image, 0.25, 0.25, 75, 95);
    assertEquals(100, scaled.getWidth());
    assertEquals(120, scaled.getHeight());
    assertEquals(0xFF00FF00, scaled.getRGB(0, 0));
    assertEquals(0xFF00FF00, scaled.getRGB(24, 24));
    assertEquals(0xFFFF0000, scaled.getRGB(13, 13));
  }
}
