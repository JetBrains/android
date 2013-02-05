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

import com.intellij.designer.designSurface.ScalableComponent;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static java.awt.RenderingHints.*;

/**
 * @author Alexander Lobas
 */
public class RootView extends com.intellij.designer.designSurface.RootView implements ScalableComponent {
  private List<EmptyRegion> myEmptyRegions;
  private final AndroidDesignerEditorPanel myPanel;

  public RootView(AndroidDesignerEditorPanel panel, int x, int y, BufferedImage image) {
    super(x, y, image);
    myPanel = panel;
  }

  public void setImage(BufferedImage image) {
    super.setImage(image);
    myEmptyRegions = new ArrayList<EmptyRegion>();
  }

  @Override
  protected void updateSize() {
    if (myImage != null) {
      double zoom = getScale();
      setBounds(myX, myY, (int)(zoom * myImage.getWidth()), (int)(zoom * myImage.getHeight()));
    }
  }

  public void addEmptyRegion(int x, int y, int width, int height) {
    if (new Rectangle(0, 0, myImage.getWidth(), myImage.getHeight()).contains(x, y)) {
      EmptyRegion r = new EmptyRegion();
      r.myX = x;
      r.myY = y;
      r.myWidth = width;
      r.myHeight = height;
      r.myColor = new Color(~myImage.getRGB(x, y));
      myEmptyRegions.add(r);
    }
  }

  @Override
  protected void paintImage(Graphics g) {
    double scale = myPanel.getZoom();
    if (scale == 1) {
      g.drawImage(myImage, 0, 0, null);
    } else {
      // TODO: Do proper thumbnail rendering here if the scaling factor < 0.5.
      Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
      g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

      int newWidth = (int)(scale * myImage.getWidth());
      int newHeight = (int)(scale * myImage.getHeight());
      g.drawImage(myImage, 0, 0, newWidth, newHeight, null);
      // TODO: Clear rendering hints?
    }

    if (!myEmptyRegions.isEmpty()) {
      if (scale == 1) {
        for (EmptyRegion r : myEmptyRegions) {
          g.setColor(r.myColor);
          g.fillRect(r.myX, r.myY, r.myWidth, r.myHeight);
        }
      } else {
        for (EmptyRegion r : myEmptyRegions) {
          g.setColor(r.myColor);
          g.fillRect((int)(scale * r.myX),
                     (int)(scale * r.myY),
                     (int)(scale * r.myWidth),
                     (int)(scale * r.myHeight));
        }
      }
    }
  }

  // Implements ScalableComponent
  @Override
  public double getScale() {
    if (myPanel != null) {
      return myPanel.getZoom();
    }
    return 1;
  }

  private static class EmptyRegion {
    public Color myColor;
    public int myX;
    public int myY;
    public int myWidth;
    public int myHeight;
  }
}
