/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

/**
 * Encapsulates basic mouse interaction on a Scene
 */
class SceneMouseInteraction {
  int myLastX;
  int myLastY;
  Scene myScene;
  DisplayList myDisplayList = new DisplayList();

  public SceneMouseInteraction(Scene scene) {
    myScene = scene;
    myScene.paint(myDisplayList, System.currentTimeMillis());
  }

  /**
   * Simulate a click on a given resize handle of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param type        the type of resize handle we want to click on
   */
  public void mouseDown(String componentId, ResizeTarget.Type type) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      ResizeTarget target = component.getResizeTarget(type);
      myLastX = target.getCenterX();
      myLastY = target.getCenterY();
      myScene.mouseDown(myLastX, myLastY);
      myScene.paint(myDisplayList, System.currentTimeMillis());
    }
  }

  /**
   * Simulate a click on a given anchor of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param type        the type of anchor we want to click on
   */
  public void mouseDown(String componentId, AnchorTarget.Type type) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      AnchorTarget target = component.getAnchorTarget(type);
      myLastX = target.getCenterX();
      myLastY = target.getCenterY();
      myScene.mouseDown(myLastX, myLastY);
      myScene.paint(myDisplayList, System.currentTimeMillis());
    }
  }

  /**
   * Simulate a click on the center of component with componentId
   *
   * @param componentId the id of the component we want to click on
   */
  public void mouseDown(String componentId) {
    mouseDown(componentId, 0, 0);
  }

  /**
   * Simulate a click on the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param offsetX     x offset from the center of the component
   * @param offsetY     y offset from the center of the component
   */
  public void mouseDown(String componentId, int offsetX, int offsetY) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      myLastX = component.getCenterX() + offsetX;
      myLastY = component.getCenterY() + offsetY;
      myScene.mouseDown(myLastX, myLastY);
      myScene.paint(myDisplayList, System.currentTimeMillis());
    }
  }

  /**
   * Simulate releasing the mouse above the given anchor of the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   * @param type        the type of anchor we need to be above
   */
  public void mouseRelease(String componentId, AnchorTarget.Type type) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      AnchorTarget target = component.getAnchorTarget(type);
      int x = target.getCenterX();
      int y = target.getCenterY();
      mouseRelease(x, y);
    }
  }

  /**
   * Simulate releasing the mouse at the coordinates (x, y).
   * Before doing the release, a serie of drag events will be simulated
   *
   * @param x coordinate on release
   * @param y coordinate on release
   */
  public void mouseRelease(int x, int y) {
    // drag first
    int steps = 10;
    float dx = x - myLastX;
    float dy = y - myLastY;
    float deltaX = dx / (float)steps;
    float deltaY = dy / (float)steps;
    dx = myLastX;
    dy = myLastY;
    for (int i = 0; i < steps; i++) {
      myScene.mouseDrag((int)dx, (int)dy);
      myScene.paint(myDisplayList, System.currentTimeMillis());
      dx += deltaX;
      dy += deltaY;
    }
    myScene.mouseDrag(x, y);
    myScene.mouseRelease(x, y);
    myScene.paint(myDisplayList, System.currentTimeMillis());
  }

  public DisplayList getDisplayList() {
    return myDisplayList;
  }
}
