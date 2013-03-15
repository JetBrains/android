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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.RenderSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.android.tools.idea.rendering.ShadowPainter.SHADOW_SIZE;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;

/** A rendered image from layoutlib, which can be zoomed */
public class ScalableImage {
  @NotNull private final BufferedImage myImage;
  @Nullable private BufferedImage myScaledImage;
  private final boolean myAlphaChannelImage;
  private double myScale = 1;

  public ScalableImage(@NotNull BufferedImage image, boolean alphaChannelImage) {
    myImage = image;
    myAlphaChannelImage = alphaChannelImage;
  }

  public ScalableImage(RenderSession session) {
    myImage = session.getImage();
    myAlphaChannelImage = session.isAlphaChannelImage();
  }

  /**
   * Returns whether this image overlay should be painted with a drop shadow.
   * This is usually the case, but not for transparent themes like the dialog
   * theme (Theme.*Dialog), which already provides its own shadow.
   *
   * @return true if the image overlay should be shown with a drop shadow.
   */
  public boolean getShowDropShadow() {
    return !myAlphaChannelImage;
  }

  public double getScale() {
    return myScale;
  }

  public void setScale(double scale) {
    if (myScale != scale) {
      myScaledImage = null;
      myScale = scale;

      // Normalize the scale:
      // Some operations are faster if the zoom is EXACTLY 1.0 rather than ALMOST 1.0.
      // (This is because there is a fast-path when image copying and the scale is 1.0;
      // in that case it does not have to do any scaling).
      //
      // If you zoom out 10 times and then back in 10 times, small rounding errors mean
      // that you end up with a scale=1.0000000000000004. In the cases, when you get close
      // to 1.0, just make the zoom an exact 1.0.
      if (Math.abs(myScale - 1.0) < 0.01) {
        myScale = 1.0;
      }
    }
  }

  /**
   * Zooms the view to fit.
   *
   * @param availableWidth the available view width
   * @param availableHeight the available view height
   * @param allowZoomIn if true, apply the scale such that it always fills the available space; if
   *                     false, allow zoom out, but never zoom in more than 100% (the real size)
   * @param horizontalMargin optional horizontal margin to reserve room for
   * @param verticalMargin optional vertical margin to reserve room for
   */
  public void zoomToFit(int availableWidth, int availableHeight, boolean allowZoomIn, int horizontalMargin, int verticalMargin) {
    int sceneWidth = myImage.getWidth();
    int sceneHeight = myImage.getHeight();
    if (getShowDropShadow()) {
      availableWidth -= SHADOW_SIZE;
      availableHeight -= SHADOW_SIZE;
    }

    if (sceneWidth > 0 && sceneHeight > 0) {
      // Reduce the margins if necessary
      int hDelta = availableWidth - sceneWidth;
      int xMargin = 0;
      if (hDelta > 2 * horizontalMargin) {
        xMargin = horizontalMargin;
      } else if (hDelta > 0) {
        xMargin = hDelta / 2;
      }

      int vDelta = availableHeight - sceneHeight;
      int yMargin = 0;
      if (vDelta > 2 * verticalMargin) {
        yMargin = verticalMargin;
      } else if (vDelta > 0) {
        yMargin = vDelta / 2;
      }

      double hScale = (availableWidth - 2 * xMargin) / (double) sceneWidth;
      double vScale = (availableHeight - 2 * yMargin) / (double) sceneHeight;

      double scale = Math.min(hScale, vScale);

      if (!allowZoomIn) {
        scale = Math.min(1.0, scale);
      }

      setScale(scale);
    }
  }

  private static final double ZOOM_FACTOR = 1.2;

  public void zoomIn() {
    setScale(myScale * ZOOM_FACTOR);
  }

  public void zoomOut() {
    setScale(myScale / ZOOM_FACTOR);
  }

  public void zoomActual() {
    setScale(1);
  }

  /** Returns the original full size rendered image */
  @NotNull
  public BufferedImage getOriginalImage() {
    return myImage;
  }

  /** Returns the original width of the image itself, not scaled */
  public int getOriginalWidth() {
    return myImage.getWidth();
  }

  /** Returns the original height of the image itself, not scaled */
  public int getOriginalHeight() {
    return myImage.getHeight();
  }

  /** Returns the width of the image itself, when scaled */
  public int getScaledWidth() {
    return (int)(myScale * myImage.getWidth());
  }

  /** Returns the height of the image itself, when scaled */
  public int getScaledHeight() {
    return (int)(myScale * myImage.getHeight());
  }

  /** Returns the required width to show the scaled image, including drop shadows if applicable */
  public int getRequiredWidth() {
    return getScaledWidth() + (getShowDropShadow() ? SHADOW_SIZE : 0);
  }

  /** Returns the required height to show the scaled image, including drop shadows if applicable */
  public int getRequiredHeight() {
    return getScaledHeight() + (getShowDropShadow() ? SHADOW_SIZE : 0);
  }

  /** Returns the required size to show the scaled image, including drop shadows if applicable */
  public Dimension getRequiredSize() {
    return new Dimension(getRequiredWidth(), getRequiredHeight());
  }

  public void paint(@NotNull Graphics g) {
    if (myScaledImage == null) {
      // Special cases myScale=1 to be fast
      if (myScale == 1) {
        // Scaling to 100% is easy!
        myScaledImage = myImage;

        if (getShowDropShadow()) {
          // Just need to draw drop shadows
          myScaledImage = ShadowPainter.createRectangularDropShadow(myImage);
        }
        g.drawImage(myScaledImage, 0, 0, null);
      } else if (myScale < 1) {
        // When scaling down we need to do an expensive scaling to ensure that
        // the thumbnails look good
        if (getShowDropShadow()) {
          myScaledImage = ImageUtils.scale(myImage, myScale, myScale,
                                           SHADOW_SIZE, SHADOW_SIZE);
          ShadowPainter.drawRectangleShadow(myScaledImage, 0, 0, myScaledImage.getWidth() - SHADOW_SIZE,
                                            myScaledImage.getHeight() - SHADOW_SIZE);
        } else {
          myScaledImage = ImageUtils.scale(myImage, myScale, myScale);
        }
        g.drawImage(myScaledImage, 0, 0, null);
      } else {
        // Do a direct scaled paint when scaling up; we don't want to create giant internal images
        // for a zoomed in version of the canvas, since only a small portion is typically shown on the screen
        // (without this, you can easily zoom in 10 times and hit an OOM exception)
        int w = myImage.getWidth();
        int h = myImage.getHeight();
        int scaledWidth = (int)(myScale * w);
        int scaledHeight = (int)(myScale * h);
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
          g2.drawImage(myImage, 0, 0, scaledWidth, scaledHeight, 0, 0, w, h, null);
        } finally {
          g2.dispose();
        }
        ShadowPainter.drawRectangleShadow(g, 0, 0, scaledWidth, scaledHeight);
      }
    } else {
      g.drawImage(myScaledImage, 0, 0, null);
    }
  }
}
