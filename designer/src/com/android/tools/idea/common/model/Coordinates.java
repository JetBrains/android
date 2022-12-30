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
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Coordinates {

  public static final float DEFAULT_DENSITY = Density.DEFAULT_DENSITY;

  /**
   * Returns the Swing x coordinate relative to {@link DesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingX(@NotNull SceneView view, @AndroidCoordinate int androidX) {
    return view.getX() + view.getContentTranslationX() + (int)Math.round(view.getScale() * androidX);
  }

  /**
   * Returns the Swing x coordinate relative to {@link DesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static float getSwingX(@NotNull SceneView view, @AndroidCoordinate float androidX) {
    return view.getX() + view.getContentTranslationX() + (float)view.getScale() * androidX;
  }

  /**
   * Returns the Swing x coordinate relative to  {@link DesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingY(@NotNull SceneView view, @AndroidCoordinate int androidY) {
    return view.getY() + view.getContentTranslationY() + (int)Math.round(view.getScale() * androidY);
  }

  /**
   * Returns the Swing x coordinate relative to  {@link DesignSurface#getLayeredPane()}
   * of the given x coordinate in the Android screen coordinate system
   */
  @SwingCoordinate
  public static float getSwingY(@NotNull SceneView view, @AndroidCoordinate float androidY) {
    return view.getY() + view.getContentTranslationY() + (float)view.getScale() * androidY;
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull SceneView view, @AndroidCoordinate int androidDimension) {
    return (int)Math.round(view.getScale() * androidDimension);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull DesignSurface<?> surface, @AndroidCoordinate int androidDimension) {
    return (int)Math.round(surface.getScale() * androidDimension);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static int getSwingDimension(@NotNull SceneContext sceneContext, @AndroidCoordinate int androidDimension) {
    return (int)Math.round(sceneContext.getScale() * androidDimension);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static float getSwingDimension(@NotNull SceneView view, @AndroidCoordinate float androidDimension) {
    return (float)view.getScale() * androidDimension;
  }

  // DPI
  @AndroidCoordinate
  public static int dpToPx(@NotNull SceneView view, @AndroidDpCoordinate int androidDp) {
    return dpToPx(view.getSceneManager(), androidDp);
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
    return dpToPx(view.getSceneManager(), androidDp);
  }

  @AndroidDpCoordinate
  public static int pxToDp(@NotNull SceneView view, @AndroidCoordinate int androidPx) {
    return pxToDp(view.getSceneManager(), androidPx);
  }

  @AndroidCoordinate
  public static int dpToPx(@NotNull SceneManager manager, @AndroidDpCoordinate float androidDp) {
    return Math.round(androidDp * manager.getSceneScalingFactor());
  }

  @AndroidDpCoordinate
  public static int pxToDp(@NotNull SceneManager manager, @AndroidCoordinate int androidPx) {
    return Math.round(androidPx / manager.getSceneScalingFactor());
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingXDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpX) {
    return getSwingX(view, dpToPx(view.getSceneManager(), androidDpX));
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static float getSwingXDip(@NotNull SceneView view, @AndroidDpCoordinate float androidDpX) {
    return getSwingX(view, view.getSceneScalingFactor() * androidDpX);
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingYDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpY) {
    return getSwingY(view, dpToPx(view.getSceneManager(), androidDpY));
  }

  /**
   * Returns the Swing x coordinate (in the {@link DesignSurface} coordinate
   * system) of the given x coordinate in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static float getSwingYDip(@NotNull SceneView view, @AndroidDpCoordinate float androidDpX) {
    return getSwingY(view, view.getSceneScalingFactor() * androidDpX);
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static int getSwingDimensionDip(@NotNull SceneView view, @AndroidDpCoordinate int androidDpDimension) {
    return getSwingDimension(view, dpToPx(view.getSceneManager(), androidDpDimension));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static float getSwingDimensionDip(@NotNull SceneView view, @AndroidDpCoordinate float androidDpDimension) {
    return getSwingDimension(view, view.getSceneScalingFactor() * androidDpDimension);
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
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static Rectangle2D.Float getSwingRectDip(@NotNull SceneView view, @NotNull @AndroidDpCoordinate Rectangle2D.Float rect) {
    return new Rectangle2D.Float(getSwingXDip(view, rect.x),
                                      getSwingYDip(view, rect.y),
                                      getSwingDimensionDip(view, rect.width),
                                      getSwingDimensionDip(view, rect.height));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static Rectangle2D.Float getSwingRectDip(@NotNull SceneContext context, @NotNull @AndroidDpCoordinate Rectangle2D.Float rect) {
    return new Rectangle2D.Float(context.getSwingXDip(rect.x),
                                 context.getSwingYDip(rect.y),
                                 context.getSwingDimensionDip(rect.width),
                                 context.getSwingDimensionDip(rect.height));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system in Dip
   */
  @SwingCoordinate
  public static Rectangle2D.Float getSwingRectDip(@NotNull SceneContext context, @NotNull @AndroidDpCoordinate Rectangle rect) {
    return new Rectangle2D.Float(context.getSwingXDip(rect.x),
                                 context.getSwingYDip(rect.y),
                                 context.getSwingDimensionDip(rect.width),
                                 context.getSwingDimensionDip(rect.height));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static Rectangle getSwingRect(@NotNull SceneView view, @NotNull @AndroidCoordinate Rectangle rect) {
    return new Rectangle(getSwingX(view, rect.x), getSwingY(view, rect.y),
                         getSwingDimension(view, rect.width), getSwingDimension(view, rect.height));
  }

  /**
   * Returns the Swing dimension (in the {@link DesignSurface} coordinate
   * system) of the given dimension in the Android screen coordinate system
   */
  @SwingCoordinate
  public static Rectangle getSwingRect(@NotNull SceneContext context, @NotNull @AndroidCoordinate Rectangle rect) {
    return new Rectangle(context.getSwingX(rect.x), context.getSwingY(rect.y),
                         getSwingDimension(context, rect.width), getSwingDimension(context, rect.height));
  }

  /**
   * Returns the Android x coordinate for the given Swing x coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidX(@NotNull SceneView view, @SwingCoordinate int swingX) {
    return (int)Math.round((swingX - view.getX() - view.getContentTranslationX()) / view.getScale());
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
    return (int)Math.round((swingY - view.getY() - view.getContentTranslationY()) / view.getScale());
  }

  /**
   * Returns the Android y coordinate for the given Swing y coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidDpCoordinate
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
    return (int)Math.round(swingDimension / view.getScale());
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidCoordinate
  public static int getAndroidDimension(@NotNull DesignSurface<?> surface, @SwingCoordinate int swingDimension) {
    return (int)Math.round(swingDimension / surface.getScale());
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidDpCoordinate
  public static int getAndroidDimensionDip(@NotNull SceneView view, @SwingCoordinate int swingDimension) {
    return pxToDp(view, getAndroidDimension(view, swingDimension));
  }

  /**
   * Returns the Android dimension for the given Swing dimension coordinate (in
   * the {@link DesignSurface} coordinate system.)
   */
  @AndroidDpCoordinate
  public static int getAndroidDimensionDip(@NotNull Scene scene, @SwingCoordinate int swingDimension) {
    return pxToDp(scene.getSceneManager(), getAndroidDimension(scene.getDesignSurface(), swingDimension));
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
    SceneContext sceneContext = SceneContext.get(view);
    @AndroidDpCoordinate int x = getAndroidXDip(view, swingX);
    @AndroidDpCoordinate int y = getAndroidYDip(view, swingY);

    SceneComponent sceneComponent = view.getScene().findComponent(sceneContext, x, y);
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
