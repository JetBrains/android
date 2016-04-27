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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.model.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;

import java.awt.*;
import java.util.List;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView {
  private final DesignSurface mySurface;
  private final ScreenViewType myType;
  private final NlModel myModel;

  public enum ScreenViewType { NORMAL, BLUEPRINT }

  @SwingCoordinate private int x;
  @SwingCoordinate private int y;

  public ScreenView(@NotNull DesignSurface surface, @NotNull ScreenViewType type, @NotNull NlModel model) {
    mySurface = surface;
    myType = type;
    myModel = model;

    myModel.addListener(new ModelListener() {
      @Override
      public void modelRendered(@NotNull NlModel model) {
        ApplicationManager.getApplication().invokeLater(() -> {
          mySurface.updateErrorDisplay(ScreenView.this, myModel.getRenderResult());
          mySurface.repaint();
        });
      }

      @Override
      public void modelChanged(@NotNull NlModel model) {
        model.render();
      }
    });
    myModel.getSelectionModel().addListener(new SelectionListener() {
      @Override
      public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            mySurface.repaint();
          }
        });
      }
    });
  }

  @Nullable
  public RenderResult getResult() {
    return myModel.getRenderResult();
  }

  /**
   * Returns the current type of this ScreenView
   */
  @NotNull
  public ScreenViewType getScreenViewType() { return myType; }

  /**
   * Returns the current size of the view. This is the same as {@link #getPreferredSize()} but accounts for the current zoom level.
   * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the values will be set and this instance
   *                  returned.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }

    Dimension preferred = getPreferredSize(dimension);
    double scale = mySurface.getScale();

    dimension.setSize((int)(scale * preferred.width), (int)(scale * preferred.height));
    return dimension;
  }

  /**
   * Returns the current size of the view. This is the same as {@link #getPreferredSize()} but accounts for the current zoom level.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getSize() {
    return getSize(null);
  }

  /**
   * Returns the current preferred size for the view.
   * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the values will be set and this instance
   *                  returned.
   */
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
  public Dimension getPreferredSize() {
    return getPreferredSize(null);
  }

  public void switchDevice() {
    List<Device> devices = myModel.getFacet().getConfigurationManager().getDevices();
    List<Device> applicable = Lists.newArrayList();
    for (Device device : devices) {
      if (HardwareConfigHelper.isNexus(device)) {
        applicable.add(device);
      }
    }
    Configuration configuration = getConfiguration();
    Device currentDevice = configuration.getDevice();
    for (int i = 0, n = applicable.size(); i < n; i++) {
      if (applicable.get(i) == currentDevice) {
        Device newDevice = applicable.get((i + 1) % applicable.size());
        configuration.setDevice(newDevice, true);
        break;
      }
    }
  }

  public void toggleOrientation() {
    Configuration configuration = getConfiguration();
    configuration.getDeviceState();

    State current = configuration.getDeviceState();
    State flip = configuration.getNextDeviceState(current);
    if (flip != null) {
      configuration.setDeviceState(flip);
    }
  }

  @NotNull
  public Configuration getConfiguration() {
    return myModel.getConfiguration();
  }

  @NotNull
  public NlModel getModel() {
    return myModel;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    // For now, the selection model is tied to the model itself.
    // This is deliberate: rather than having each view have its own
    // independent selection, when a file is shown multiple times on the screen,
    // selection is "synchronized" between the views by virtue of them all
    // sharing the same selection model, currently stashed in the model itself.
    return myModel.getSelectionModel();
  }

  public DesignSurface getSurface() {
    return mySurface;
  }

  public double getScale() {
    return mySurface.getScale();
  }

  public void setLocation(@SwingCoordinate int screenX, @SwingCoordinate int screenY) {
    x = screenX;
    y = screenY;
  }

  @SwingCoordinate
  public int getX() {
    return x;
  }

  @SwingCoordinate
  public int getY() {
    return y;
  }
}
