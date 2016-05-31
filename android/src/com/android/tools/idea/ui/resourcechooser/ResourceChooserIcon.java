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
package com.android.tools.idea.ui.resourcechooser;

import sun.awt.image.IntegerComponentRaster;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

public class ResourceChooserIcon implements Icon {
  private final int mySize;
  private final Image myImage;
  private final Paint myCheckerPaint;
  private boolean myInterpolate;


  public ResourceChooserIcon(int size, Image image, int checkerboardSize, boolean interpolate) {
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

  /**
   * A {@link Paint} that will paint a checkerboard pattern. The current implementation aligns the pattern to the window (device)
   * coordinates, so the checkerboard remains stationary, even when the panel and the image is scrolled.
   */
  public static class CheckerboardPaint implements Paint, PaintContext {
    private final int myCheckerSize;
    private final int myDoubleCheckerSize;
    private static final int LIGHT_COLOR = 0xFFFFFFFF;
    private static final int DARK_COLOR = 0xFFC0C0C0;

    public CheckerboardPaint(int size) {
      myCheckerSize = size;
      myDoubleCheckerSize = 2 * myCheckerSize;
    }

    // Cached raster and pixel values. They are re-allocated whenever a larger size is required. The raster's data is updated each time
    // a raster is requested in #getRaster(int, int, int, int).
    // A checkerboard can be broken down into rows of squares of alternating colors. There are two alternating rows: those that start with
    // a dark color and those that start with the light color. We cache the pixel values of a single raster scan line for both types of
    // rows, so they don't need to be computed every time.
    private WritableRaster cachedRaster;
    private int[] cachedEvenRow = new int[0];
    private int[] cachedOddRow = new int[0];

    @Override
    public PaintContext createContext(
      ColorModel cm, Rectangle deviceBounds, Rectangle2D userBounds, AffineTransform xform, RenderingHints hints) {
      return this;
    }

    @Override
    public void dispose() {
      cachedRaster = null;
    }

    @Override
    public ColorModel getColorModel() {
      return ColorModel.getRGBdefault();
    }

    @Override
    public Raster getRaster(int x, int y, int w, int h) {
      WritableRaster raster = cachedRaster;
      if (raster == null || w > raster.getWidth() || h > raster.getHeight()) {
        cachedRaster = raster = getColorModel().createCompatibleWritableRaster(w, h);
      }
      w = raster.getWidth();
      h = raster.getHeight();

      // Compute the x & y pixel offsets into a 2x2 checker tile. The checkerboard is aligned to (0, 0).
      int xOffset = x % myDoubleCheckerSize, yOffset = y % myDoubleCheckerSize;
      int[] evenRow = cachedEvenRow, oddRow = cachedOddRow;
      if (evenRow.length < xOffset + w || oddRow.length < xOffset + w) {
        // The scan line caches are sized in multiples of 2 checker squares.
        evenRow = new int[myDoubleCheckerSize * ((xOffset + w + myDoubleCheckerSize - 1) / myDoubleCheckerSize)];
        oddRow = new int[evenRow.length];
        // Fill in the cached scan lines, two squares at a time.
        for (int i = 0; i < evenRow.length; i += myDoubleCheckerSize) {
          // The even row is light, dark, light, dark, etc.
          Arrays.fill(evenRow, i, i + myCheckerSize, LIGHT_COLOR);
          Arrays.fill(evenRow, i + myCheckerSize, i + myDoubleCheckerSize, DARK_COLOR);
          // The odd row is dark, light, dark, light, etc.
          Arrays.fill(oddRow, i, i + myCheckerSize, DARK_COLOR);
          Arrays.fill(oddRow, i + myCheckerSize, i + myDoubleCheckerSize, LIGHT_COLOR);
        }
      }

      // The pixels array is a w * h row major storage backend of the raster data.
      int[] pixels = ((IntegerComponentRaster)raster).getDataStorage();
      int[][] rows = new int[][] { evenRow, oddRow };
      // The current checker row being copied. Initialized to align to the requested (x, y) coordinates.
      int curRowPointer = (yOffset < myCheckerSize) ? 0 : 1;
      int[] curRow = rows[curRowPointer];
      // Copy the cached scan lines into the raster.
      for (int i = 0, done = 0, tileY = yOffset % myCheckerSize; i < h; i++, tileY++, done += w) {
        if (tileY >= myCheckerSize) {
          // We've completed a row of checker squares, switch to the other row type.
          //noinspection AssignmentToForLoopParameter
          tileY = 0;
          curRowPointer = (curRowPointer + 1) & 1;
          curRow = rows[curRowPointer];
        }
        // The scan lines are aligned to 2x2 checker tiles, so we copy starting at xOffset.
        System.arraycopy(curRow, xOffset, pixels, done, w);
      }
      return raster;
    }

    @Override
    public int getTransparency() {
      return Transparency.OPAQUE;
    }
  }
}
