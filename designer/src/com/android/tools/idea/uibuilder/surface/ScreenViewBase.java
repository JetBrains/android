/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.AndroidColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.RESIZING_HOVERING_SIZE;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
abstract class ScreenViewBase extends SceneView {
  private final ColorSet myColorSet = new AndroidColorSet();

  public ScreenViewBase(@NotNull NlDesignSurface surface, @NotNull NlModel model) {
    super(surface, model);
  }

  /**
   * Returns the current preferred size for the view.
   *
   * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the values will be set and this instance
   *                  returned.
   */
  @Override
  @NotNull
  public Dimension getPreferredSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }

    Configuration configuration = getConfiguration();
    Device device = configuration.getDevice();
    State state = configuration.getDeviceState();
    if (device != null && state != null) {
      HardwareConfig config =
        new HardwareConfigHelper(device).setOrientation(state.getOrientation()).getConfig();

      dimension.setSize(config.getScreenWidth(), config.getScreenHeight());
    }

    return dimension;
  }

  @Override
  @Nullable
  public Cursor getCursor(@SwingCoordinate int x, @SwingCoordinate int y) {
    Rectangle resizeZone =
      new Rectangle(getX() + getSize().width, getY() + getSize().height, RESIZING_HOVERING_SIZE, RESIZING_HOVERING_SIZE);
    if (resizeZone.contains(x, y)) {
      return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
    }
    return super.getCursor(x, y);
  }

  @Override
  public LayoutlibSceneManager getSceneManager() {
    return (LayoutlibSceneManager)super.getSceneManager();
  }

  @NotNull
  @Override
  public NlDesignSurface getSurface() {
    return (NlDesignSurface)super.getSurface();
  }

  @Override
  @NotNull
  public ColorSet getColorSet() {
    return myColorSet;
  }

  @Nullable
  public RenderResult getResult() {
    return getSceneManager().getRenderResult();
  }

  public void paintBorder(@NotNull Graphics2D g) {
    BorderPainter.paint(g, this);
  }

  public static class BorderPainter {

    private static final int SHADOW_SIZE = JBUI.scale(6);
    private static final Color COLOR_OUTSIDE = UIUtil.TRANSPARENT_COLOR;
    private static final Color COLOR_INSIDE = new JBColor(new Color(70, 70, 70, 10), new Color(10, 10, 10, 20));
    private static final Paint GRAD_LEFT = new GradientPaint(0, 0, COLOR_OUTSIDE, SHADOW_SIZE, 0, COLOR_INSIDE);
    private static final Paint GRAD_TOP = new GradientPaint(0, 0, COLOR_OUTSIDE, 0, SHADOW_SIZE, COLOR_INSIDE);
    private static final Paint GRAD_RIGHT = new GradientPaint(0, 0, COLOR_INSIDE, SHADOW_SIZE, 0, COLOR_OUTSIDE);
    private static final Paint GRAD_BOTTOM = new GradientPaint(0, 0, COLOR_INSIDE, 0, SHADOW_SIZE, COLOR_OUTSIDE);
    private static final Paint GRAD_CORNER =
      new RadialGradientPaint(SHADOW_SIZE, SHADOW_SIZE, SHADOW_SIZE, new float[]{0, 1}, new Color[]{COLOR_INSIDE, COLOR_OUTSIDE});

    public static void paint(@NotNull Graphics2D g2d, @NotNull SceneView screenView) {
      Dimension size = screenView.getSize();
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
