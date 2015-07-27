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
package com.android.tools.idea.ui;

import com.android.assetstudiolib.Util;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * VectorImageComponent is a Swing component that displays an image.
 * Particularly added a 3D boundary and center the image in the middle.
 */
public class VectorImageComponent extends ImageComponent {
  TexturePaint mChessBoardPatternPaint;
  private static final int TEXTURE_SIZE = 16;

  public VectorImageComponent() {
    // Set up a texture paint for chess board pattern.
    int imageSize = TEXTURE_SIZE;
    BufferedImage img = UIUtil.createImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB);
    int[] data = ((DataBufferInt) (img.getRaster().getDataBuffer()))
      .getData();
    // Fill the data as chess board pattern
    for (int i = 0; i < imageSize * imageSize; i++) {
      // x, y will be either 0 or 1. Combination of x and y can denote which
      // quadrant the current pixel is in.
      int x = (i % imageSize) / (imageSize / 2);
      int y = (i / imageSize) / (imageSize / 2);
      data[i] = ((x + y) % 2 == 0) ? 0xAAAAAA : 0xEEEEEE;
    }
    mChessBoardPatternPaint = new TexturePaint(img, new Rectangle2D.Float(0, 0, TEXTURE_SIZE, TEXTURE_SIZE));
  }

  @Override
  protected void paintChildren(@NotNull Graphics g) {
    // Draw the chess board background all the time.
    Graphics2D g2d = (Graphics2D) g;
    g2d.setPaint(mChessBoardPatternPaint);
    g2d.fillRect(0, 0, getWidth(), getHeight());

    // Then draw the icon to the center.
    if (myIcon == null) return;

    final BufferedImage image = UIUtil.createImage(myIcon.getIconWidth(), myIcon.getIconHeight(),
                                                   BufferedImage.TYPE_INT_ARGB);
    final Graphics2D imageGraphics = image.createGraphics();
    myIcon.paintIcon(this, imageGraphics, 0, 0);

    g.draw3DRect(0, 0, getWidth() - 1, getHeight() - 1, false);

    Rectangle rect = new Rectangle(0, 0, getWidth(), getHeight());
    Util.drawCenterInside(g2d, image, rect);
  }
}
