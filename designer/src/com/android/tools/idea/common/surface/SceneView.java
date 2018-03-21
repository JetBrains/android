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
package com.android.tools.idea.common.surface;

import com.android.resources.ScreenRound;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * View of a {@link Scene} used in a {@link DesignSurface}.
 */
public abstract class SceneView {
  private final DesignSurface mySurface;
  private final SceneManager myManager;
  private ImmutableList<Layer> myLayers;
  @SwingCoordinate private int x;
  @SwingCoordinate private int y;

  public SceneView(@NotNull DesignSurface surface, @NotNull SceneManager manager) {
    mySurface = surface;
    myManager = manager;
  }

  @NotNull
  protected abstract ImmutableList<Layer> createLayers();

  /**
   * If Layers are not exist, they will be created by {@link #createLayers()}. This should happen only once.
   */
  @NotNull
  public final ImmutableList<Layer> getLayers() {
    if (myLayers == null) {
      myLayers = createLayers();
    }
    return myLayers;
  }

  @NotNull
  public Scene getScene() {
    return myManager.getScene();
  }

  /**
   * Returns the current size of the view. This is the same as {@link #getPreferredSize()} but accounts for the current zoom level.
   *
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
    double scale = getScale();

    dimension.setSize((int)(scale * preferred.width), (int)(scale * preferred.height));
    return dimension;
  }

  @NotNull
  public Dimension getPreferredSize() {
    return getPreferredSize(null);
  }

  /**
   * Returns the current size of the view. This is the same as {@link #getPreferredSize()} but accounts for the current zoom level.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getSize() {
    return getSize(null);
  }

  @NotNull
  abstract public Dimension getPreferredSize(@Nullable Dimension dimension);

  @NotNull
  public Configuration getConfiguration() {
    return getSceneManager().getModel().getConfiguration();
  }

  /**
   * @deprecated This method will be removed in the future. The Model should be obtained by {@link SceneManager#getModel()} instead.
   */
  @Deprecated
  @NotNull
  public NlModel getModel() {
    return myManager.getModel();
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return getSurface().getSelectionModel();
  }

  /**
   * Returns null if the screen is rectangular; if not, it returns a shape (round for AndroidWear etc)
   */
  @Nullable
  public Shape getScreenShape() {
    Device device = getConfiguration().getDevice();
    if (device == null) {
      return null;
    }

    Screen screen = device.getDefaultHardware().getScreen();
    if (screen.getScreenRound() != ScreenRound.ROUND) {
      return null;
    }

    Dimension size = getSize();

    int chin = screen.getChin();
    int originX = getX();
    int originY = getY();
    if (chin == 0) {
      // Plain circle
      return new Ellipse2D.Double(originX, originY, size.width, size.height);
    }
    else {
      int height = size.height * chin / screen.getYDimension();
      Area a1 = new Area(new Ellipse2D.Double(originX, originY, size.width, size.height + height));
      Area a2 = new Area(new Rectangle2D.Double(originX, originY + 2 * (size.height + height) - height, size.width, height));
      a1.subtract(a2);
      return a1;
    }
  }

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  public double getScale() {
    return getSurface().getScale();
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

  /**
   * Custom translation to apply when converting between android coordinate space and swing coordinate space.
   */
  @SwingCoordinate
  public int getContentTranslationX() {
    return 0;
  }

  /**
   * Custom translation to apply when converting between android coordinate space and swing coordinate space.
   */
  @SwingCoordinate
  public int getContentTranslationY() {
    return 0;
  }

  @Nullable
  public Cursor getCursor(@SwingCoordinate int x, @SwingCoordinate int y) {
    SceneContext.get(this).setMouseLocation(x, y);
    getScene().mouseHover(SceneContext.get(this), Coordinates.getAndroidXDip(this, x), Coordinates.getAndroidYDip(this, y));
    return getScene().getMouseCursor();
  }

  public SceneManager getSceneManager() {
    return myManager;
  }

  /**
   * Sets the tool tip to be shown
   */
  public void setToolTip(String toolTip) {
    getSurface().setDesignToolTip(toolTip);
  }

  @NotNull
  public abstract ColorSet getColorSet();
}
