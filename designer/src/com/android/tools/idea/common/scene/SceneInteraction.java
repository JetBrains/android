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

import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.SceneView;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Implements a mouse interaction started on a Scene
 */
public class SceneInteraction extends Interaction {

  /**
   * The surface associated with this interaction.
   */
  protected SceneView mySceneView;

  /**
   * Base constructor
   *
   * @param sceneView the ScreenView we belong to
   * @param component  the component we belong to
   */
  public SceneInteraction(@NotNull SceneView sceneView) {
    mySceneView = sceneView;
  }

  /**
   * Start the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param startMask The initial AWT mask for the interaction
   */
  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int startMask) {
    super.begin(x, y, startMask);
    int androidX = Coordinates.getAndroidX(mySceneView, myStartX);
    int androidY = Coordinates.getAndroidY(mySceneView, myStartY);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.updateModifiers(startMask);
    scene.mouseDown(SceneContext.get(mySceneView), dpX, dpY);
  }

  /**
   * Update the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiers current modifier key mask
   */
  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiers) {
    super.update(x, y, modifiers);
    int androidX = Coordinates.getAndroidX(mySceneView, x);
    int androidY = Coordinates.getAndroidY(mySceneView, y);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.updateModifiers(modifiers);
    scene.mouseDrag(SceneContext.get(mySceneView), dpX, dpY);
    mySceneView.getSurface().repaint();
  }

  /**
   * Ends the mouse interaction and commit the modifications if any
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiers current modifier key mask
   * @param canceled  True if the interaction was canceled, and false otherwise.
   */
  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);
    final int androidX = Coordinates.getAndroidX(mySceneView, x);
    final int androidY = Coordinates.getAndroidY(mySceneView, y);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.updateModifiers(modifiers);
    scene.mouseRelease(SceneContext.get(mySceneView), dpX, dpY);
    mySceneView.getSurface().repaint();
  }
}
