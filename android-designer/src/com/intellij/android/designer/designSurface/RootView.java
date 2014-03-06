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

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.ScalableImage;
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
  @Nullable private List<EmptyRegion> myEmptyRegions;
  @NotNull private final AndroidDesignerEditorPanel myPanel;
  protected int myX;
  protected int myY;
  @Nullable ScalableImage myScalableImage;

  public RootView(@NotNull AndroidDesignerEditorPanel panel, int x, int y, @NotNull RenderResult renderResult) {
    myX = x;
    myY = y;
    myPanel = panel;
    myScalableImage = renderResult.getImage();
  }

  private RootView(@NotNull AndroidDesignerEditorPanel panel) {
    myPanel = panel;
  }

  @NotNull
  public AndroidDesignerEditorPanel getPanel() {
    return myPanel;
  }

  @Nullable
  public BufferedImage getImage() {
    return myScalableImage != null ? myScalableImage.getOriginalImage() : null;
  }

  @Nullable
  public ScalableImage getScalableImage() {
    return myScalableImage;
  }

  /**
   * Sets the image to be drawn
   * <p>
   * The image <b>can</b> be null, which is the case when we are dealing with
   * an empty document.
   *
   * @param image The image to be rendered
   */
  public void setRenderedImage(@Nullable ScalableImage image) {
    myEmptyRegions = null;
    myScalableImage = image;
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
    if (myScalableImage != null) {
      return myScalableImage.getShowDropShadow();
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
    if (myScalableImage == null) {
      return;
    }
    if (myPanel.isZoomToFit()) {
      myPanel.zoomToFitIfNecessary();
    }

    double zoom = myPanel.getZoom();
    myScalableImage.setScale(zoom);
    Dimension requiredSize = myScalableImage.getRequiredSize();
    int newWidth = requiredSize.width;
    int newHeight = requiredSize.height;
    if (getWidth() != newWidth || getHeight() != newHeight) {
      setSize(newWidth, newHeight);
      myScalableImage.imageChanged();
    } else if (imageChanged) {
      myScalableImage.imageChanged();
    }
  }

  public void addEmptyRegion(int x, int y, int width, int height) {
    if (myScalableImage == null) {
      return;
    }
    BufferedImage image = myScalableImage.getOriginalImage();
    if (new Rectangle(0, 0, image.getWidth(), image.getHeight()).contains(x, y)) {
      EmptyRegion r = new EmptyRegion();
      r.myX = x;
      r.myY = y;
      r.myWidth = width;
      r.myHeight = height;
      //noinspection UseJBColor
      r.myColor = new Color(~image.getRGB(x, y));
      if (myEmptyRegions == null) {
        myEmptyRegions = new ArrayList<EmptyRegion>();
      }
      myEmptyRegions.add(r);
    }
  }

  protected void paintImage(Graphics g) {
    if (myScalableImage == null) {
      return;
    }

    double scale = myPanel.getZoom();
    myScalableImage.setScale(scale);
    myScalableImage.paint(g, 0, 0);

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
    if (myScalableImage != null) {
      myScalableImage.setScale(myPanel.getZoom());
      return myScalableImage.getScaledWidth();
    }

    return 0;
  }

  /** Returns the height of the image itself, when scaled */
  public int getScaledHeight() {
    if (myScalableImage != null) {
      myScalableImage.setScale(myPanel.getZoom());
      return myScalableImage.getScaledHeight();
    }

    return 0;
  }

  // Implements ScalableComponent

  @Override
  public double getScale() {
    double zoom = myPanel.getZoom();
    if (myScalableImage != null) {
      Rectangle viewBounds = myScalableImage.getImageBounds();
      if (viewBounds != null) {
        double deviceFrameFactor = viewBounds.getWidth() / (double) myScalableImage.getScaledWidth();
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
    if (myScalableImage != null) {
      Rectangle viewBounds = myScalableImage.getImageBounds();
      if (viewBounds != null) {
        return viewBounds.x;
      }
    }
    return 0;
  }

  @Override
  public int getShiftY() {
    if (myScalableImage != null) {
      Rectangle viewBounds = myScalableImage.getImageBounds();
      if (viewBounds != null) {
        return viewBounds.y;
      }
    }
    return 0;
  }

  private static class EmptyRegion {
    public Color myColor;
    public int myX;
    public int myY;
    public int myWidth;
    public int myHeight;
  }
}
