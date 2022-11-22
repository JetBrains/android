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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;

public class BorderLayer extends Layer {

  private final SceneView myScreenView;

  private final boolean myMustPaintBorder;

  public BorderLayer(@NotNull SceneView screenView) {
    this(screenView, false);
  }

  public BorderLayer(@NotNull SceneView screenView, boolean mustPaintBorder) {
    myScreenView = screenView;
    myMustPaintBorder = mustPaintBorder;
  }

  @Override
  public void paint(@NotNull Graphics2D g2d) {
    Shape screenShape = myScreenView.getScreenShape();
    if (!myMustPaintBorder && screenShape != null) {
      g2d.draw(screenShape);
      return;
    }

    // When screen rotation feature is enabled, we want to hide the border.
    DesignSurface<?> surface = myScreenView.getSurface();
    if (surface instanceof NlDesignSurface) {
      NlDesignSurface nlSurface = (NlDesignSurface) surface;
      float degree = nlSurface.getRotateSurfaceDegree();
      if (!Float.isNaN(degree)) {
        return;
      }
    }

    BorderPainter.paint(g2d, myScreenView);
  }

  private static class BorderPainter {

    private static final int SHADOW_SIZE = JBUI.scale(4);
    private static final Color COLOR_OUTSIDE = UIUtil.TRANSPARENT_COLOR;
    private static final Color COLOR_INSIDE = JBColor.namedColor("ScreenView.borderColor", new JBColor(new Color(0, 0, 0, 40), new Color(0, 0, 0, 80)));
    private static final Paint GRAD_LEFT = new GradientPaint(0, 0, COLOR_OUTSIDE, SHADOW_SIZE, 0, COLOR_INSIDE);
    private static final Paint GRAD_TOP = new GradientPaint(0, 0, COLOR_OUTSIDE, 0, SHADOW_SIZE, COLOR_INSIDE);
    private static final Paint GRAD_RIGHT = new GradientPaint(0, 0, COLOR_INSIDE, SHADOW_SIZE, 0, COLOR_OUTSIDE);
    private static final Paint GRAD_BOTTOM = new GradientPaint(0, 0, COLOR_INSIDE, 0, SHADOW_SIZE, COLOR_OUTSIDE);
    private static final Paint GRAD_CORNER =
      new RadialGradientPaint(SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE, new float[]{0, 1}, new Color[]{COLOR_INSIDE, COLOR_OUTSIDE});

    public static void paint(@NotNull Graphics2D g2d, @NotNull SceneView screenView) {
      Dimension size = screenView.getScaledContentSize();

      int x = screenView.getX();
      int y = screenView.getY();

      RenderingHints hints = g2d.getRenderingHints();
      AffineTransform tx = g2d.getTransform();
      Paint paint = g2d.getPaint();

      // Left
      g2d.translate(x - SHADOW_SIZE, y);
      g2d.scale(1, size.height / (double)SHADOW_SIZE);
      g2d.setPaint(GRAD_LEFT);
      g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE);

      // Right
      g2d.translate(size.width + SHADOW_SIZE, 0);
      g2d.setPaint(GRAD_RIGHT);
      g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE);

      // Reset transform scale and translate to upper left corner
      g2d.translate(-size.width, 0);
      g2d.scale(1, SHADOW_SIZE / (double)size.height);

      // Top
      g2d.translate(0, -SHADOW_SIZE);
      g2d.scale(size.width / (double)SHADOW_SIZE, 1);
      g2d.setPaint(GRAD_TOP);
      g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE);

      // Bottom
      g2d.translate(0, size.height + SHADOW_SIZE);
      g2d.setPaint(GRAD_BOTTOM);
      g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE);

      // Reset the transform
      g2d.setTransform(tx);

      // Smoothen the corner shadows
      g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Paint the corner shadows
      g2d.setPaint(GRAD_CORNER);
      // Top Left
      g2d.translate(x - SHADOW_SIZE, y - SHADOW_SIZE);
      g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 90, 90);
      // Top Right
      g2d.translate(size.width, 0);
      g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 0, 90);
      // Bottom Right
      g2d.translate(0, size.height);
      g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 270, 90);
      // Bottom Left
      g2d.translate(-size.width, 0);
      g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 180, 90);

      g2d.setTransform(tx);
      g2d.setRenderingHints(hints);
      g2d.setPaint(paint);
    }
  }
}
