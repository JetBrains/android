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
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.AndroidColorSet;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
abstract class ScreenViewBase extends SceneView {

  /**
   * Distance between the bottom bound of model name and top bound of SceneView.
   */
  @SwingCoordinate private static final int NAME_LABEL_BOTTOM_MARGIN_PX = 5;

  private final ColorSet myColorSet = new AndroidColorSet();
  protected boolean myIsSecondary;

  public ScreenViewBase(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    super(surface, manager);
  }

  @Override
  public int getNameLabelHeight() {
    if (getSurface().isShowModelNames()) {
      Graphics graphics = getSurface().getGraphics();
      if (graphics != null) {
        Font font = graphics.getFont();
        FontMetrics metrics = graphics.getFontMetrics(font);
        return metrics.getHeight() + NlConstants.NAME_LABEL_BOTTOM_MARGIN_PX;
      }
    }
    return 0;
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

  @NotNull
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

  /**
   * Set if this is the second SceneView in the associcated Scene/SceneManager.
   * @param isSecondary the new value to indicated if this is the second SceneView in associated Scene/SceneManager.
   */
  final void setSecondary(boolean isSecondary) {
    myIsSecondary = isSecondary;
  }

  /**
   * @return true if this is second SceneView in the associated Scene/SceneManager, false otherwise. The default value is false.
   */
  protected final boolean isSecondary() {
    return myIsSecondary;
  }
}
