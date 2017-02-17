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
package com.android.tools.idea.uibuilder.model;

import com.android.resources.Density;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class Coordinates {

  public static final float DEFAULT_DENSITY = Density.DEFAULT_DENSITY;

  /**
   * Returns the Swing x coordinate relative to {@link NlDesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingX(@NotNull SceneView view, @AndroidCoordinate int androidX) {
    return view.getX() + (int)(view.getScale() * androidX);
  }

  /**
   * Returns the Swing x coordinate relative to  {@link NlDesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingY(@NotNull SceneView view, @AndroidCoordinate int androidY) {
    return view.getY() + (int)(view.getScale() * androidY);
  }

  /**
   * Returns the Swing dimension (in the {@link NlDesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull SceneView view, @AndroidCoordinate int androidDimension) {
    return (int)(view.getScale() * androidDimension);
  }

  // DPI
  public static int dpToPx(@NotNull SceneView view, @AndroidDpCoordinate int androidDp) {
    final Configuration configuration = view.getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    return Math.round(androidDp * (dpiValue / DEFAULT_DENSITY));
  }

  @AndroidCoordinate
  public static int dpToPx(@NotNull NlModel model, @AndroidDpCoordinate float androidDp) {
    final Configuration configuration = model.getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    return Math.round(androidDp * (dpiValue / DEFAULT_DENSITY));
  }

  @AndroidDpCoordinate
  public static int pxToDp(@NotNull NlModel model, @AndroidCoordinate int androidPx) {
    final Configuration configuration = model.getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    return Math.round(androidPx * (DEFAULT_DENSITY / dpiValue));
  }

  @AndroidCoordinate
  public static int dpToPx(@NotNull SceneView view, @AndroidDpCoordinate float androidDp) {
    return dpToPx(view.getModel(), androidDp);
  }

  @AndroidDpCoordinate
  public static int pxToDp(@NotNull SceneView view, @AndroidCoordinate int androidPx) {
    return pxToDp(view.getModel(), androidPx);
  }

  /**
   * Returns the Swing x coordinate (in the {@link NlDesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingXDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpX) {
    return getSwingX(view, dpToPx(view, androidDpX));
  }

  /**
   * Returns the Swing x coordinate (in the {@link NlDesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingYDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpY) {
    return getSwingY(view, dpToPx(view, androidDpY));
  }

  /**
   * Returns the Swing dimension (in the {@link NlDesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingDimensionDip(@NotNull SceneView view, @AndroidCoordinate int androidDpDimension) {
    return getSwingDimension(view, dpToPx(view, androidDpDimension));
  }

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link NlDesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidX(@NotNull SceneView view, @SwingCoordinate int swingX) {
    return (int)((swingX - view.getX()) / view.getScale());
  }

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link NlDesignSurface} coordinate system.)
   */
  @AndroidDpCoordinate
  public static int getAndroidXDip(@NotNull SceneView view, @SwingCoordinate int swingX) {
    return pxToDp(view, getAndroidX(view, swingX));
  }

  /**
   * Returns the Android y coordinate for the given Swing y coordinate (in
   * the {@link NlDesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidY(@NotNull SceneView view, @SwingCoordinate int swingY) {
    return (int)((swingY - view.getY()) / view.getScale());
  }

  /**
   * Returns the Android y coordinate for the given Swing y coordinate (in
   * the {@link NlDesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidYDip(@NotNull SceneView view, @SwingCoordinate int swingY) {
    return pxToDp(view, getAndroidY(view, swingY));
  }

  /**
   * Translates a {@link Point} from a Swing coordinate (in the {@link NlDesignSurface} coordinate system) to an Android coordinate.
   */
  @AndroidCoordinate
  @NotNull
  public static Point getAndroidCoordinate(@NotNull SceneView view, @NotNull @SwingCoordinate Point coord) {
    return new Point(getAndroidX(view, coord.x), getAndroidY(view, coord.y));
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link NlDesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimension(@NotNull SceneView view, @SwingCoordinate int swingDimension) {
    return (int)(swingDimension / view.getScale());
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link NlDesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimensionDip(@NotNull SceneView view, @SwingCoordinate int swingDimension) {
    return pxToDp(view, getSwingDimension(view, swingDimension));
  }

  /**
   * Returns the component at the given (x,y) coordinate in the Swing coordinate system
   *
   * @deprecated If you're interacting with the UI (as is implied by {@code @SwingCoordinate} you should probably be using SceneComponents
   * directly, rather than NlComponents.
   */
  @Nullable
  public static NlComponent findComponent(@NotNull SceneView view,
                                          @SwingCoordinate int swingX,
                                          @SwingCoordinate int swingY) {
    SceneComponent sceneComponent =
      view.getScene().findComponent(SceneContext.get(view), getAndroidXDip(view, swingX), getAndroidYDip(view, swingY));
    return sceneComponent != null ? sceneComponent.getNlComponent() : null;
  }
}
