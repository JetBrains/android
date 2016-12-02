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
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Coordinates {

  public static final float DEFAULT_DENSITY = Density.DEFAULT_DENSITY;

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingX(@NotNull ScreenView view, @AndroidCoordinate int androidX) {
    return view.getX() + (int)(view.getScale() * androidX);
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingY(@NotNull ScreenView view, @AndroidCoordinate int androidY) {
    return view.getY() + (int)(view.getScale() * androidY);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull ScreenView view, @AndroidCoordinate int androidDimension) {
    return (int)(view.getScale() * androidDimension);
  }

  // DPI
  public static int dpToPx(@NotNull ScreenView view, @AndroidDpCoordinate int androidDp) {
    final Configuration configuration = view.getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    return Math.round(androidDp * (dpiValue / DEFAULT_DENSITY));
  }

  public static int dpToPx(@NotNull ScreenView view, @AndroidDpCoordinate float androidDp) {
    final Configuration configuration = view.getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    return Math.round(androidDp * (dpiValue / DEFAULT_DENSITY));
  }

  @AndroidDpCoordinate
  public static int pxToDp(@NotNull ScreenView view, @AndroidCoordinate int androidPx) {
    final Configuration configuration = view.getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    return Math.round(androidPx * (DEFAULT_DENSITY / dpiValue));
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingXDip(@NotNull ScreenView view, @AndroidDpCoordinate int androidDpX) {
    return getSwingX(view, dpToPx(view, androidDpX));
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingYDip(@NotNull ScreenView view, @AndroidDpCoordinate int androidDpY) {
    return getSwingY(view, dpToPx(view, androidDpY));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingDimensionDip(@NotNull ScreenView view, @AndroidCoordinate int androidDpDimension) {
    return getSwingDimension(view, dpToPx(view, androidDpDimension));
  }

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidX(@NotNull ScreenView view, @SwingCoordinate int swingX) {
    return (int)((swingX - view.getX()) / view.getScale());
  }

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidDpCoordinate
  public static int getAndroidXDip(@NotNull ScreenView view, @SwingCoordinate int swingX) {
    return pxToDp(view, getAndroidX(view, swingX));
  }

  /**
   * Returns the Android y coordinate for the given Swing y coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidY(@NotNull ScreenView view, @SwingCoordinate int swingY) {
    return (int)((swingY - view.getY()) / view.getScale());
  }

  /**
   * Returns the Android y coordinate for the given Swing y coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidYDip(@NotNull ScreenView view, @SwingCoordinate int swingY) {
    return pxToDp(view, getAndroidY(view, swingY));
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimension(@NotNull ScreenView view, @SwingCoordinate int swingDimension) {
    return (int)(swingDimension / view.getScale());
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimensionDip(@NotNull ScreenView view, @SwingCoordinate int swingDimension) {
    return pxToDp(view, getSwingDimension(view, swingDimension));
  }

  /**
   * Returns the component at the given (x,y) coordinate in the Swing coordinate system
   */
  @Nullable
  public static NlComponent findComponent(@NotNull ScreenView view,
                                          @SwingCoordinate int swingX,
                                          @SwingCoordinate int swingY) {
    return view.getModel().findLeafAt(getAndroidX(view, swingX), getAndroidY(view, swingY), false);
  }

  /**
   * Returns the component at the given (x,y) coordinate in the Swing coordinate system which is the
   * immediate child of the current selection (or the selection itself if no children found and the
   * selection intersects).
   */
  @Nullable
  public static NlComponent findImmediateComponent(@NotNull ScreenView view,
                                                   @SwingCoordinate int swingX,
                                                   @SwingCoordinate int swingY) {
    if (view.getModel().getComponents().isEmpty()) {
      return null;
    }
    SelectionModel selectionModel = view.getSelectionModel();
    NlComponent start = null;
    if (selectionModel.isEmpty()) {
      // If we don't have a selection, start the search from root
      start = view.getModel().getComponents().get(0).getRoot();
    } else {
      start = selectionModel.getPrimary();
    }
    NlComponent found = start.findImmediateLeafAt(getAndroidX(view, swingX), getAndroidY(view, swingY));
    if (found == null) {
      found = findComponent(view, swingX, swingY);
    }
    return found;
  }
}
