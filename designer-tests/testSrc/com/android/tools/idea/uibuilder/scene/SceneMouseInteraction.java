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

  public void mouseRelease(String componentId, AnchorTarget.Type type) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      AnchorTarget target = component.getAnchorTarget(type);
      int x = target.getCenterX();
      int y = target.getCenterY();
      // drag
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
  }

  public DisplayList getDisplayList() {
    return myDisplayList;
  }
}
