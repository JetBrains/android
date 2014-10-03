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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.rendering.Overlay;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedImage;
import com.google.common.collect.Lists;
import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Root component used for the Android designer.
 */
public class RootView extends JComponent implements TransformedComponent {
  public static final int EMPTY_COMPONENT_SIZE = 5;
  public static final int VISUAL_EMPTY_COMPONENT_SIZE = 14;

  @Nullable private List<EmptyRegion> myEmptyRegions;
  @NotNull private final AndroidDesignerEditorPanel myPanel;
  protected int myX;
  protected int myY;
  @Nullable RenderedImage myRenderedImage;

  public RootView(@NotNull AndroidDesignerEditorPanel panel, int x, int y, @NotNull RenderResult renderResult) {
    myX = x;
    myY = y;
    myPanel = panel;
    myRenderedImage = renderResult.getImage();
  }

  @NotNull
  public AndroidDesignerEditorPanel getPanel() {
    return myPanel;
  }

  @Nullable
  public BufferedImage getImage() {
    return myRenderedImage != null ? myRenderedImage.getOriginalImage() : null;
  }

  @Nullable
  public RenderedImage getRenderedImage() {
    return myRenderedImage;
  }

  /**
   * Sets the image to be drawn
   * <p>
   * The image <b>can</b> be null, which is the case when we are dealing with
   * an empty document.
   *
   * @param image The image to be rendered
   */
  public void setRenderedImage(@Nullable RenderedImage image) {
    clearEmptyRegions();
    myRenderedImage = image;
    updateBounds(true);
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
    if (myRenderedImage != null) {
      return myRenderedImage.getShowDropShadow();
    } else {
      return false;
    }
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
    if (myRenderedImage == null) {
      return;
    }
    if (myPanel.isZoomToFit()) {
      myPanel.zoomToFitIfNecessary();
    }

    double zoom = myPanel.getZoom();
    myRenderedImage.setScale(zoom);
    Dimension requiredSize = myRenderedImage.getRequiredSize();
    int newWidth = requiredSize.width;
    int newHeight = requiredSize.height;
    if (getWidth() != newWidth || getHeight() != newHeight) {
      setSize(newWidth, newHeight);
      myRenderedImage.imageChanged();
    } else if (imageChanged) {
      myRenderedImage.imageChanged();
    }
  }

  public void clearEmptyRegions() {
    myEmptyRegions = null;
  }

  public void addEmptyRegion(int x, int y, int width, int height) {
    if (myRenderedImage == null) {
      return;
    }
    BufferedImage image = myRenderedImage.getOriginalImage();
    int imageWidth = image.getWidth();
    int imageHeight = image.getHeight();
    if (x >= 0 && x <= imageWidth && y >= 0 && y <= imageHeight) {
      EmptyRegion r = new EmptyRegion();
      r.myX = Math.max(0, Math.min(x, imageWidth - VISUAL_EMPTY_COMPONENT_SIZE));
      r.myY = Math.max(0, Math.min(y, imageHeight - VISUAL_EMPTY_COMPONENT_SIZE));
      r.myWidth = width;
      r.myHeight = height;
      //noinspection UseJBColor
      r.myColor = new Color(~image.getRGB(r.myX, r.myY));
      if (myEmptyRegions == null) {
        myEmptyRegions = new ArrayList<EmptyRegion>();
      }
      myEmptyRegions.add(r);
    }
  }

  protected void paintImage(Graphics g) {
    if (myRenderedImage == null) {
      return;
    }

    Shape clip = g.getClip();
    if (clip != null) {
      Rectangle clipBounds = g.getClipBounds();
      int deltaX = getX();
      int deltaY = getY();
      g.setClip(clipBounds.x - deltaX, clipBounds.y - deltaY, clipBounds.width += deltaX, clipBounds.height + deltaY);
    }

    double scale = myPanel.getZoom();
    myRenderedImage.setScale(scale);
    myRenderedImage.paint(g, 0, 0);

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

    Overlay.paintOverlays(myPanel, this, g, 0, 0);

    if (clip != null) {
      g.setClip(clip);
    }
  }

  /** Returns the width of the image itself, when scaled */
  public int getScaledWidth() {
    if (myRenderedImage != null) {
      myRenderedImage.setScale(myPanel.getZoom());
      return myRenderedImage.getScaledWidth();
    }

    return 0;
  }

  /** Returns the height of the image itself, when scaled */
  public int getScaledHeight() {
    if (myRenderedImage != null) {
      myRenderedImage.setScale(myPanel.getZoom());
      return myRenderedImage.getScaledHeight();
    }

    return 0;
  }

  // Implements ScalableComponent

  @Override
  public double getScale() {
    double zoom = myPanel.getZoom();

    if (myRenderedImage != null) {
      Rectangle viewBounds = myRenderedImage.getImageBounds();
      if (viewBounds != null) {
        double deviceFrameFactor = viewBounds.getWidth() / (double) myRenderedImage.getScaledWidth();
        if (deviceFrameFactor != 1) {
          zoom *= deviceFrameFactor;
        }
      }
    }
    return zoom;
  }

  // Implements TransformedComponent

  @Override
  public int getShiftX() {
    if (myRenderedImage != null) {
      Rectangle viewBounds = myRenderedImage.getImageBounds();
      if (viewBounds != null) {
        return viewBounds.x;
      }
    }
    return 0;
  }

  @Override
  public int getShiftY() {
    if (myRenderedImage != null) {
      Rectangle viewBounds = myRenderedImage.getImageBounds();
      if (viewBounds != null) {
        return viewBounds.y;
      }
    }
    return 0;
  }

  @VisibleForTesting
  public List<Rectangle> getEmptyRegions() {
    List<Rectangle> list = Lists.newArrayList();
    if (myEmptyRegions != null) {
      for (EmptyRegion region : myEmptyRegions) {
        list.add(new Rectangle(region.myX, region.myY, region.myWidth, region.myHeight));
      }
    }
    return list;
  }

  private static class EmptyRegion {
    public Color myColor;
    public int myX;
    public int myY;
    public int myWidth;
    public int myHeight;
  }
}
