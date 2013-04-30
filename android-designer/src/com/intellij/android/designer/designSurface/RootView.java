/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface;

import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.rendering.ShadowPainter;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.designer.designSurface.ScalableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.rendering.ShadowPainter.SHADOW_SIZE;
import static com.android.tools.idea.rendering.ShadowPainter.SMALL_SHADOW_SIZE;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;

/**
 * Root component used for the Android designer. Note that this view
 * does not extend {@link com.intellij.designer.designSurface.RootView};
 * the designer infrastructure does not require that, and that particular
 * RootView calls setImage from its constructor. We need to handle
 * the alpha channel flag in concert with the image property, since
 * layout of the image depends on the alpha channel property. (We're
 * storing the opposite of the alpha channel attribute in the drop shadow
 * property.)
 * <p>
 * TODO: Use {@link com.android.tools.idea.rendering.ScalableImage} here!
 * </p>
 */
public class RootView extends JComponent implements ScalableComponent {
  @Nullable private List<EmptyRegion> myEmptyRegions;
  @NotNull private final AndroidDesignerEditorPanel myPanel;
  @Nullable private BufferedImage myScaledImage;
  private boolean myShowDropShadow;
  protected int myX;
  protected int myY;
  @Nullable protected BufferedImage myImage;

  public RootView(@NotNull AndroidDesignerEditorPanel panel, int x, int y, @Nullable BufferedImage image, boolean isAlphaChannelImage) {
    myX = x;
    myY = y;
    myPanel = panel;
    myImage = image;
    myShowDropShadow = !isAlphaChannelImage;
  }

  public AndroidDesignerEditorPanel getPanel() {
    return myPanel;
  }

  @Nullable
  public BufferedImage getImage() {
    return myImage;
  }

  /**
   * Sets the image to be drawn
   * <p>
   * The image <b>can</b> be null, which is the case when we are dealing with
   * an empty document.
   *
   * @param image The image to be rendered
   * @param isAlphaChannelImage whether the alpha channel of the image is relevant
   */
  public void setImage(@Nullable BufferedImage image, boolean isAlphaChannelImage) {
    myShowDropShadow = !isAlphaChannelImage;
    myEmptyRegions = null;
    myImage = image;
    updateSize();
    repaint();
  }

  /**
   * Returns whether this image overlay should be painted with a drop shadow.
   * This is usually the case, but not for transparent themes like the dialog
   * theme (Theme.*Dialog), which already provides its own shadow.
   *
   * @return true if the image overlay should be shown with a drop shadow.
   */
  public boolean getShowDropShadow() {
    return myShowDropShadow;
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintImage(g);
  }

  public void updateSize() {
    updateBounds(true);
  }

  protected void updateBounds(boolean imageChanged) {
    if (myImage != null) {
      if (myPanel.isZoomToFit()) {
        myPanel.zoomToFitIfNecessary();
      }
      double zoom = getScale();
      int newWidth = (int)(zoom * myImage.getWidth());
      int newHeight = (int)(zoom * myImage.getHeight());
      if (myShowDropShadow) {
        int shadowSize = myPanel.isUseLargeShadows() ? SHADOW_SIZE : SMALL_SHADOW_SIZE;
        newWidth += shadowSize;
        newHeight += shadowSize;
      }
      if (getWidth() != newWidth || getHeight() != newHeight) {
        setSize(newWidth, newHeight);
        myScaledImage = null;
      } else if (imageChanged) {
        myScaledImage = null;
      }
    }
  }

  public void addEmptyRegion(int x, int y, int width, int height) {
    if (myImage != null && new Rectangle(0, 0, myImage.getWidth(), myImage.getHeight()).contains(x, y)) {
      EmptyRegion r = new EmptyRegion();
      r.myX = x;
      r.myY = y;
      r.myWidth = width;
      r.myHeight = height;
      r.myColor = new Color(~myImage.getRGB(x, y));
      if (myEmptyRegions == null) {
        myEmptyRegions = new ArrayList<EmptyRegion>();
      }
      myEmptyRegions.add(r);
    }
  }

  protected void paintImage(Graphics g) {
    if (myImage == null) {
      return;
    }

    double scale = myPanel.getZoom();
    if (myScaledImage == null) {
      // Special cases scale=1 to be fast
      if (scale == 1) {
        // Scaling to 100% is easy!
        myScaledImage = myImage;

        if (myShowDropShadow) {
          // Just need to draw drop shadows
          if (myPanel.isUseLargeShadows()) {
            myScaledImage = ShadowPainter.createRectangularDropShadow(myImage);
          } else {
            myScaledImage = ShadowPainter.createSmallRectangularDropShadow(myImage);
          }
        }
        g.drawImage(myScaledImage, 0, 0, null);
      } else if (scale < 1) {
        // When scaling down we need to do an expensive scaling to ensure that
        // the thumbnails look good
        if (myShowDropShadow) {
          if (myPanel.isUseLargeShadows()) {
            myScaledImage = ImageUtils.scale(myImage, scale, scale, SHADOW_SIZE, SHADOW_SIZE);
            ShadowPainter.drawRectangleShadow(myScaledImage, 0, 0,
                                              myScaledImage.getWidth() - SHADOW_SIZE,
                                              myScaledImage.getHeight() - SHADOW_SIZE);
          } else {
            myScaledImage = ImageUtils.scale(myImage, scale, scale, SMALL_SHADOW_SIZE, SMALL_SHADOW_SIZE);
            ShadowPainter.drawSmallRectangleShadow(myScaledImage, 0, 0,
                                                   myScaledImage.getWidth() - SMALL_SHADOW_SIZE,
                                                    myScaledImage.getHeight() - SMALL_SHADOW_SIZE);
          }
        } else {
          myScaledImage = ImageUtils.scale(myImage, scale, scale);
        }
        g.drawImage(myScaledImage, 0, 0, null);
      } else {
        // Do a direct scaled paint when scaling up; we don't want to create giant internal images
        // for a zoomed in version of the canvas, since only a small portion is typically shown on the screen
        // (without this, you can easily zoom in 10 times and hit an OOM exception)
        int w = myImage.getWidth();
        int h = myImage.getHeight();
        int scaledWidth = (int)(scale * w);
        int scaledHeight = (int)(scale * h);
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
          g2.drawImage(myImage, 0, 0, scaledWidth, scaledHeight, 0, 0, w, h, null);
        } finally {
          g2.dispose();
        }
        if (getShowDropShadow()) {
          if (myPanel.isUseLargeShadows()) {
            ShadowPainter.drawRectangleShadow(g, 0, 0, scaledWidth, scaledHeight);
          } else {
            ShadowPainter.drawSmallRectangleShadow(g, 0, 0, scaledWidth, scaledHeight);
          }
        }
      }
    } else {
      g.drawImage(myScaledImage, 0, 0, null);
    }

    if (myEmptyRegions != null && !myEmptyRegions.isEmpty()) {
      if (scale == 1) {
        for (EmptyRegion r : myEmptyRegions) {
          DesignerGraphics.drawFilledRect(DrawingStyle.EMPTY, g, r.myX, r.myY, r.myWidth, r.myHeight);
        }
      } else {
        for (EmptyRegion r : myEmptyRegions) {
          DesignerGraphics.drawFilledRect(DrawingStyle.EMPTY, g, (int)(scale * r.myX), (int)(scale * r.myY),
                                          (int)(scale * r.myWidth), (int)(scale * r.myHeight));
        }
      }
    }
  }

  /** Returns the width of the image itself, when scaled */
  public int getScaledWidth() {
    return myImage != null ? (int)(getScale() * myImage.getWidth()) : 0;
  }

  /** Returns the height of the image itself, when scaled */
  public int getScaledHeight() {
    return myImage != null ? (int)(getScale() * myImage.getHeight()) : 0;
  }

  // Implements ScalableComponent
  @Override
  public double getScale() {
    return myPanel.getZoom();
  }

  private static class EmptyRegion {
    public Color myColor;
    public int myX;
    public int myY;
    public int myWidth;
    public int myHeight;
  }
}
