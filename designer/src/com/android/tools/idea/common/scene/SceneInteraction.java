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
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.InteractionInformation;
import com.android.tools.idea.common.surface.KeyPressedEvent;
import com.android.tools.idea.common.surface.KeyReleasedEvent;
import com.android.tools.idea.common.surface.MouseDraggedEvent;
import com.android.tools.idea.common.surface.MousePressedEvent;
import com.android.tools.idea.common.surface.MouseReleasedEvent;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements a mouse interaction started on a Scene
 */
public class SceneInteraction implements Interaction {

  /**
   * The surface associated with this interaction.
   */
  protected SceneView mySceneView;

  /**
   * Base constructor
   *
   * @param sceneView the ScreenView we belong to
   */
  public SceneInteraction(@NotNull SceneView sceneView) {
    mySceneView = sceneView;
  }

  @Override
  public void begin(@NotNull InteractionEvent event) {
    if (event instanceof MousePressedEvent) {
      MouseEvent mouseEvent = ((MousePressedEvent)event).getEventObject();
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
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    int androidX = Coordinates.getAndroidX(mySceneView, x);
    int androidY = Coordinates.getAndroidY(mySceneView, y);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.mouseDown(mySceneView.getContext(), dpX, dpY, modifiersEx);
  }

  @Override
  public void update(@NotNull InteractionEvent event) {
    if (event instanceof MouseDraggedEvent) {
      MouseEvent mouseEvent = ((MouseDraggedEvent)event).getEventObject();
      int mouseX = mouseEvent.getX();
      int mouseY = mouseEvent.getY();
      mySceneView.getContext().setMouseLocation(mouseX, mouseY);
      update(mouseX, mouseY, mouseEvent.getModifiersEx());
    }
    else if (event instanceof KeyPressedEvent || event instanceof KeyReleasedEvent) {
      // Since holding some of these keys might change some visuals, repaint.
      Scene scene = mySceneView.getScene();
      scene.needsRebuildList();
      scene.repaint();
    }
  }

  /**
   * Update the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   */
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    int androidX = Coordinates.getAndroidX(mySceneView, x);
    int androidY = Coordinates.getAndroidY(mySceneView, y);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.mouseDrag(mySceneView.getContext(), dpX, dpY, modifiersEx);
    mySceneView.getSurface().repaint();
  }

  @Override
  public void commit(@NotNull InteractionEvent event) {
    int x;
    int y;
    int modifiers;
    if (!(event instanceof MouseReleasedEvent)) {
      Logger.getInstance(getClass()).error("The instance of event should be MouseReleasedEvent but it is " + event.getClass() +
                                           "; The SceneView is " + mySceneView);
      InteractionInformation info = event.getInfo();
      x = info.getX();
      y = info.getY();
      modifiers = info.getModifiersEx();
    }
    else {
      MouseEvent mouseEvent = ((MouseReleasedEvent)event).getEventObject();
      x = mouseEvent.getX();
      y = mouseEvent.getY();
      modifiers = mouseEvent.getModifiersEx();
    }
    end(x, y, modifiers);
  }

  /**
   * Ends the mouse interaction and commit the modifications if any
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   */
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    final int androidX = Coordinates.getAndroidX(mySceneView, x);
    final int androidY = Coordinates.getAndroidY(mySceneView, y);
    int dpX = Coordinates.pxToDp(mySceneView, androidX);
    int dpY = Coordinates.pxToDp(mySceneView, androidY);
    Scene scene = mySceneView.getScene();
    scene.mouseRelease(mySceneView.getContext(), dpX, dpY, modifiersEx);
    mySceneView.getSurface().repaint();
  }

  @Override
  public void cancel(@NotNull InteractionEvent event) {
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
