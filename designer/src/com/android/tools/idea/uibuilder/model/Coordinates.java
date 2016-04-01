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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;

public class Coordinates {
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

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidX(@NotNull ScreenView view, @SwingCoordinate int swingX) {
    return (int)((swingX - view.getX()) / view.getScale());
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
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimension(@NotNull ScreenView view, @SwingCoordinate int swingDimension) {
    return (int)(swingDimension / view.getScale());
  }

  /** Returns the component at the given (x,y) coordinate in the Swing coordinate system */
  @Nullable
  public static NlComponent findComponent(@NotNull ScreenView view,
                                          @SwingCoordinate int swingX, 
                                          @SwingCoordinate int swingY) {
    return view.getModel().findLeafAt(getAndroidX(view, swingX), getAndroidY(view, swingY), false);
  }
}
