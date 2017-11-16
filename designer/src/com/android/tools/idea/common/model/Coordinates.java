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
package com.android.tools.idea.common.model;

import com.android.resources.Density;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;

public class Coordinates {

  public static final float DEFAULT_DENSITY = Density.DEFAULT_DENSITY;

  /**
   * Returns the Swing x coordinate relative to {@link DesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingX(@NotNull SceneView view, @AndroidCoordinate int androidX) {
    return view.getX() + view.getContentTranslationX() + (int)(view.getScale() * androidX);
  }

  /**
   * Returns the Swing x coordinate relative to  {@link DesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingY(@NotNull SceneView view, @AndroidCoordinate int androidY) {
    return view.getY() + view.getContentTranslationY() + (int)(view.getScale() * androidY);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull SceneView view, @AndroidCoordinate int androidDimension) {
    return (int)(view.getScale() * androidDimension);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull DesignSurface surface, @AndroidCoordinate int androidDimension) {
    return (int)(surface.getScale() * androidDimension);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull SceneContext sceneContext, @AndroidCoordinate int androidDimension) {
    return (int)(sceneContext.getScale() * androidDimension);
  }

  // DPI
  @AndroidCoordinate
  public static int dpToPx(@NotNull SceneView view, @AndroidDpCoordinate int androidDp) {
    return dpToPx(view.getSurface(), androidDp);
  }

  @Deprecated  // Use #pxToDp(DesignSurface, int)
  @AndroidDpCoordinate
  public static int pxToDp(@NotNull NlModel model, @AndroidCoordinate int androidPx) {
    final Configuration configuration = model.getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    return Math.round(androidPx * (DEFAULT_DENSITY / dpiValue));
  }

  @AndroidCoordinate
  public static int dpToPx(@NotNull SceneView view, @AndroidDpCoordinate float androidDp) {
    return dpToPx(view.getSurface(), androidDp);
  }

  @AndroidDpCoordinate
  public static int pxToDp(@NotNull SceneView view, @AndroidCoordinate int androidPx) {
    return pxToDp(view.getSurface(), androidPx);
  }

  @AndroidCoordinate
  public static int dpToPx(@NotNull DesignSurface surface, @AndroidDpCoordinate float androidDp) {
    return Math.round(androidDp * surface.getSceneScalingFactor());
  }

  @AndroidDpCoordinate
  public static int pxToDp(@NotNull DesignSurface surface, @AndroidCoordinate int androidPx) {
    return Math.round(androidPx / surface.getSceneScalingFactor());
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingXDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpX) {
    return getSwingX(view, dpToPx(view.getSurface(), androidDpX));
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingYDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpY) {
    return getSwingY(view, dpToPx(view.getSurface(), androidDpY));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingDimensionDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpDimension) {
    return getSwingDimension(view, dpToPx(view.getSurface(), androidDpDimension));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static Rectangle getSwingRectDip(@NotNull SceneContext context, @NotNull @AndroidDpCoordinate Rectangle rect) {
    return new Rectangle(context.getSwingX(rect.x), context.getSwingY(rect.y),
                         context.getSwingDimension(rect.width), context.getSwingDimension(rect.height));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static Rectangle getSwingRectDip(@NotNull SceneView view, @NotNull @AndroidDpCoordinate Rectangle rect) {
    return new Rectangle(getSwingXDip(view, rect.x), getSwingYDip(view, rect.y),
                         getSwingDimensionDip(view, rect.width), getSwingDimensionDip(view, rect.height));
  }

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidX(@NotNull SceneView view, @SwingCoordinate int swingX) {
    return (int)((swingX - view.getX() - view.getContentTranslationX()) / view.getScale());
  }

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidDpCoordinate
  public static int getAndroidXDip(@NotNull SceneView view, @SwingCoordinate int swingX) {
    return pxToDp(view, getAndroidX(view, swingX));
  }

  /**
   * Returns the Android y coordinate for the given Swing y coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidY(@NotNull SceneView view, @SwingCoordinate int swingY) {
    return (int)((swingY - view.getY() - view.getContentTranslationY()) / view.getScale());
  }

  /**
   * Returns the Android y coordinate for the given Swing y coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidYDip(@NotNull SceneView view, @SwingCoordinate int swingY) {
    return pxToDp(view, getAndroidY(view, swingY));
  }

  /**
   * Translates a {@link Point} from a Swing coordinate (in the {@link DesignSurface} coordinate system) to an Android coordinate.
   */
  @AndroidCoordinate
  @NotNull
  public static Point getAndroidCoordinate(@NotNull SceneView view, @NotNull @SwingCoordinate Point coord) {
    return new Point(getAndroidX(view, coord.x), getAndroidY(view, coord.y));
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimension(@NotNull SceneView view, @SwingCoordinate int swingDimension) {
    return (int)(swingDimension / view.getScale());
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimension(@NotNull DesignSurface surface, @SwingCoordinate int swingDimension) {
    return (int)(swingDimension / surface.getScale());
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimensionDip(@NotNull SceneView view, @SwingCoordinate int swingDimension) {
    return pxToDp(view, getAndroidDimension(view, swingDimension));
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimensionDip(@NotNull DesignSurface surface, @SwingCoordinate int swingDimension) {
    return pxToDp(surface, getAndroidDimension(surface, swingDimension));
  }

  /**
   * Returns the component at the given (x,y) coordinate in the Swing coordinate system
   *
   * @deprecated If you're interacting with the UI (as is implied by {@code @SwingCoordinate} you should probably be using SceneComponents
   * directly, rather than NlComponents.
   */
  @Deprecated
  @Nullable
  public static NlComponent findComponent(@NotNull SceneView view,
                                          @SwingCoordinate int swingX,
                                          @SwingCoordinate int swingY) {
    SceneComponent sceneComponent =
      view.getScene().findComponent(SceneContext.get(view), getAndroidXDip(view, swingX), getAndroidYDip(view, swingY));
    return sceneComponent != null ? sceneComponent.getNlComponent() : null;
  }

  /**
   * Translate and scale the provided graphics context so the Android Coordinates can
   * be directly used with the {@link Graphics2D}'s api.
   *
   * It is advised to save the original transform before the call to this method and
   * restore it after.
   *
   * @param view The screen view where the Android component will be painted
   * @param gc   The graphic context to transform
   * @see Graphics2D#getTransform()
   * @see Graphics2D#setTransform(AffineTransform)
   */
  public static void transformGraphics(@NotNull SceneView view, @NotNull Graphics2D gc) {
    gc.translate(view.getX(), view.getY());
    gc.scale(view.getScale(), view.getScale());
  }
}
