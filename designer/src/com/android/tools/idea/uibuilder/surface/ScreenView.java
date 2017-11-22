/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.device.DeviceArtPainter;
import com.android.tools.idea.rendering.RenderResult;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.RESIZING_HOVERING_SIZE;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends ScreenViewBase {

  public ScreenView(@NotNull NlDesignSurface surface, @NotNull NlModel model) {
    super(surface, model);
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    builder.add(new MyBottomLayer(this));
    builder.add(new ScreenViewLayer(this));
    builder.add(new SelectionLayer(this));

    if (myModel.getType().isLayout()) {
      builder.add(new ConstraintsLayer((NlDesignSurface) mySurface, this, true));
    }

    SceneLayer sceneLayer = new SceneLayer(mySurface, this, false);
    sceneLayer.setAlwaysShowSelection(true);
    builder.add(sceneLayer);
    if (mySurface.getLayoutType().isSupportedByDesigner()) {
      builder.add(new CanvasResizeLayer((NlDesignSurface) mySurface, this));
    }
    return builder.build();
  }

  @Override
  public void updateCursor(@SwingCoordinate int x, @SwingCoordinate int y) {
    Rectangle resizeZone =
      new Rectangle(getX() + getSize().width, getY() + getSize().height, RESIZING_HOVERING_SIZE, RESIZING_HOVERING_SIZE);
    if (resizeZone.contains(x, y)) {
      mySurface.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
      return;
    }
    super.updateCursor(x, y);
  }

  private static class MyBottomLayer extends Layer {

    private final ScreenViewBase myScreenView;
    private boolean myPaintedFrame;

    public MyBottomLayer(@NotNull ScreenViewBase screenView) {
      myScreenView = screenView;
    }

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      Composite oldComposite = g2d.getComposite();
      RenderResult result = myScreenView.getResult();

      myPaintedFrame = false;
      if (myScreenView.getSurface().isDeviceFrameVisible() && result != null && result.hasImage()) {
        Configuration configuration = myScreenView.getConfiguration();
        Device device = configuration.getDevice();
        State deviceState = configuration.getDeviceState();
        DeviceArtPainter painter = DeviceArtPainter.getInstance();
        if (device != null && painter.hasDeviceFrame(device) && deviceState != null) {
          myPaintedFrame = true;
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
          painter.paintFrame(g2d, device, deviceState.getOrientation(), true, myScreenView.getX(), myScreenView.getY(),
                             (int)(myScreenView.getScale() * result.getRenderedImage().getHeight()));
        }
      }

      g2d.setComposite(oldComposite);

      if (!myPaintedFrame) {
        // Only show bounds dashed lines when there's no device
        paintBorder(g2d);
      }
    }

    private void paintBorder(Graphics2D g2d) {
      Shape screenShape = myScreenView.getScreenShape();
      if (screenShape != null) {
        g2d.draw(screenShape);
        return;
      }
      myScreenView.paintBorder(g2d);
    }
  }
}

