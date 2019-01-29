/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.icons;

import com.android.tools.idea.ui.resourcechooser.util.CheckerboardPaint;
import sun.awt.image.IntegerComponentRaster;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

class ResourceChooserImageIcon implements Icon {
  private final int mySize;
  private final Image myImage;
  private final Paint myCheckerPaint;
  private boolean myInterpolate;


  ResourceChooserImageIcon(int size, Image image, int checkerboardSize, boolean interpolate) {
    mySize = size;
    myImage = image;
    myCheckerPaint = checkerboardSize > 0 ? new CheckerboardPaint(checkerboardSize) : null;
    myInterpolate = interpolate;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    double scale = Math.min(getIconHeight() / (double)myImage.getHeight(c), getIconWidth() / (double)myImage.getWidth(c));
    int dx = (int)(getIconWidth() - (myImage.getWidth(c) * scale)) / 2;
    int dy = (int)(getIconHeight() - (myImage.getHeight(c) * scale)) / 2;

    if (myCheckerPaint != null) {
      ((Graphics2D)g).setPaint(myCheckerPaint);
      g.fillRect(x, y, getIconWidth(), getIconHeight());
    }

    if (myInterpolate) {
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    g.drawImage(myImage, x + dx, y + dy, (int)(myImage.getWidth(c) * scale), (int)(myImage.getHeight(c) * scale), null);
  }

  @Override
  public int getIconWidth() {
    return mySize;
  }

  @Override
  public int getIconHeight() {
    return mySize;
  }

  // Copy of the CheckerboardPaint from ImagePanel.ImageComponent in the gfxtrace code, but
  // with customizable sizes and colors
}
