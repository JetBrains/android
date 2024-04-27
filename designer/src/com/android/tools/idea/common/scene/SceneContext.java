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
package com.android.tools.idea.common.scene;

import com.android.sdklib.AndroidCoordinate;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * This provides the information for painting the related {@link SceneView} such like transform from dp to screen space.
 */
public abstract class SceneContext {
  // Picker is used to record all graphics drawn to support selection
  private final ScenePicker myGraphicsPicker = new ScenePicker();
  private Object myFoundObject;
  private Long myTime;
  @SwingCoordinate private int myMouseX = -1;
  @SwingCoordinate private int myMouseY = -1;
  private boolean myShowOnlySelection = false;

  @NotNull
  @SwingCoordinate
  protected final Rectangle myRenderableBounds = new Rectangle();

  public SceneContext() {
    myTime = System.currentTimeMillis();
    myGraphicsPicker.setSelectListener((over, dist) -> myFoundObject = over);
  }

  public void setShowOnlySelection(boolean value) {
    myShowOnlySelection = value;
  }

  public long getTime() {
    return myTime;
  }

  public void setTime(long time) {
    myTime = time;
  }

  public void setMouseLocation(@SwingCoordinate int x, @SwingCoordinate int y) {
    myMouseX = x;
    myMouseY = y;
  }

  /**
   * Get the X location of the mouse
   *
   * @return
   */
  @SwingCoordinate
  public int getMouseX() {
    return myMouseX;
  }

  /**
   * Get the Y location of the mouse
   *
   * @return
   */
  @SwingCoordinate
  public int getMouseY() {
    return myMouseY;
  }

  /**
   * Used to request Repaint
   */
  public void repaint() {
  }

  @SwingCoordinate
  public int getSwingXDip(@AndroidDpCoordinate float x) {
    return (int) x;
  }

  @SwingCoordinate
  public int getSwingYDip(@AndroidDpCoordinate float y) {
    return (int) y;
  }

  @SwingCoordinate
  public int getSwingX(@AndroidCoordinate int x) {
    return x;
  }

  @SwingCoordinate
  public int getSwingY(@AndroidCoordinate int y) {
    return y;
  }

  @SwingCoordinate
  public int getSwingDimensionDip(@AndroidDpCoordinate float dim) {
    return (int) dim;
  }

  @SwingCoordinate
  public int getSwingDimension(@AndroidCoordinate int dim) {
    return dim;
  }

  @NotNull
  public abstract ColorSet getColorSet();

  @NotNull
  public ScenePicker getScenePicker() {
    return myGraphicsPicker;
  }

  /**
   * Find objects drawn on the {@link Scene}.
   * Objects drawn with this {@link SceneContext} can record there shapes
   * and this find can be used to detect them.
   * @param x
   * @param y
   * @return The clicked objects if exist, or null otherwise.
   */
  @Nullable
  public Object findClickedGraphics(@SwingCoordinate int x, @SwingCoordinate int y) {
    myFoundObject = null;
    myGraphicsPicker.find(x, y);
    return myFoundObject;
  }

  public double getScale() { return 1; }

  public final void setRenderableBounds(@NotNull @SwingCoordinate Rectangle bounds) {
    myRenderableBounds.setBounds(bounds);
  }

  @NotNull
  @SwingCoordinate
  public final Rectangle getRenderableBounds() {
    return myRenderableBounds;
  }

  private static SceneContext lazySingleton;

  /**
   * Provide an Identity transform used in testing
   */
  @TestOnly
  @NotNull
  public static SceneContext get() {
    if (lazySingleton == null) {
      lazySingleton = new IdentitySceneContext();
    }
    return lazySingleton;
  }

  @Nullable
  public DesignSurface<?> getSurface() {
    return null;
  }

  @AndroidDpCoordinate
  public float pxToDp(@AndroidCoordinate int px) {
    return px * Coordinates.DEFAULT_DENSITY;
  }

  public boolean showOnlySelection() {
    return myShowOnlySelection;
  }

  /**
   * A {@link SceneContext} for testing purpose, which treat all coordinates system as the same one.
   */
  @TestOnly
  private static class IdentitySceneContext extends SceneContext {

    private ColorSet myColorSet = new BlueprintColorSet();

    @Override
    @NotNull
    public ColorSet getColorSet() {
      return myColorSet;
    }
  }
}
