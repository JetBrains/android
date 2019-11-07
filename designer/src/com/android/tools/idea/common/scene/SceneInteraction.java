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
import com.android.tools.idea.common.surface.InteractionInformation;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  public void begin(@Nullable EventObject event, @NotNull InteractionInformation interactionInformation) {
    if (event instanceof MouseEvent) {
      MouseEvent mouseEvent = (MouseEvent)event;
      begin(mouseEvent.getX(), mouseEvent.getY(), mouseEvent.getModifiersEx());
    }
    else {
      Logger.getInstance(SceneInteraction.class).warn("The Scene Interaction shouldn't be started by event " + event);
    }
  }

  /**
   * Start the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx The initial AWT mask for the interaction
   */
  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    super.begin(x, y, modifiersEx);
    int androidX = Coordinates.getAndroidX(mySceneView, myStartX);
    int androidY = Coordinates.getAndroidY(mySceneView, myStartY);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.mouseDown(SceneContext.get(mySceneView), dpX, dpY, modifiersEx);
  }

  @Override
  public void update(@NotNull EventObject event, @NotNull InteractionInformation interactionInformation) {
    if (event instanceof MouseEvent) {
      MouseEvent mouseEvent = (MouseEvent)event;
      int mouseX = mouseEvent.getX();
      int mouseY = mouseEvent.getY();
      mySceneView.getContext().setMouseLocation(mouseX, mouseY);
      update(mouseX, mouseY, mouseEvent.getModifiersEx());
    }
  }

  /**
   * Update the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   */
  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    super.update(x, y, modifiersEx);
    int androidX = Coordinates.getAndroidX(mySceneView, x);
    int androidY = Coordinates.getAndroidY(mySceneView, y);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.mouseDrag(SceneContext.get(mySceneView), dpX, dpY, modifiersEx);
    mySceneView.getSurface().repaint();
  }

  @Override
  public void commit(@Nullable EventObject event, @NotNull InteractionInformation interactionInformation) {
    assert event instanceof MouseEvent;
    MouseEvent mouseEvent = (MouseEvent)event;
    end(mouseEvent.getX(), mouseEvent.getY(), mouseEvent.getModifiersEx());
  }

  /**
   * Ends the mouse interaction and commit the modifications if any
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   */
  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    final int androidX = Coordinates.getAndroidX(mySceneView, x);
    final int androidY = Coordinates.getAndroidY(mySceneView, y);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.mouseRelease(SceneContext.get(mySceneView), dpX, dpY, modifiersEx);
    mySceneView.getSurface().repaint();
  }

  @Override
  public void cancel(@Nullable EventObject event, @NotNull InteractionInformation interactionInformation) {
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    cancel(interactionInformation.getX(), interactionInformation.getY(), interactionInformation.getModifiersEx());
  }

  /**
   * Ends the mouse interaction and commit the modifications if any
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   */
  @Override
  public void cancel(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    Scene scene = mySceneView.getScene();
    scene.mouseCancel();
    mySceneView.getSurface().repaint();
  }

  @Override
  @Nullable
  public Cursor getCursor() {
    return mySceneView.getScene().getMouseCursor();
  }
}
