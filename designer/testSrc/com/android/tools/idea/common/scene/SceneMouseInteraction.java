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

import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.AnchorTarget;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.common.scene.target.Target;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates basic mouse interaction on a Scene
 */
public class SceneMouseInteraction {
  private final Scene myScene;
  float myLastX;
  float myLastY;
  DisplayList myDisplayList = new DisplayList();

  public SceneMouseInteraction(Scene scene) {
    myScene = scene;
    repaint();
  }

  public float getLastX() { return myLastX; }
  public float getLastY() { return myLastY; }

  /**
   * Simulate a click on a given resize handle of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param targetClass the class of target we want to click on
   * @param pos         which target to click on
   */
  public void mouseDown(String componentId, Class targetClass, int pos) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      List<Target> targets = component.getTargets();
      int n = 0;
      for (Target target : targets) {
        if (targetClass.isInstance(target)) {
          if (pos == n) {
            mouseDown(target.getCenterX(), target.getCenterY());
            return;
          }
          n++;
        }
      }
    }
  }

  /**
   * Simulate a click on a given resize handle of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param type        the type of resize handle we want to click on
   */
  public void mouseDown(String componentId, ResizeBaseTarget.Type type) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      ResizeBaseTarget target = component.getResizeTarget(type);
      mouseDown(target.getCenterX(), target.getCenterY());
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
      AnchorTarget target = ConstraintComponentUtilities.getAnchorTarget(component, type);
      mouseDown(target.getCenterX(), target.getCenterY());
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
  public void mouseDown(String componentId, float offsetX, float offsetY) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      mouseDown(component.getCenterX() + offsetX, component.getCenterY() + offsetY);
    }
  }

  public void mouseDown(float x, float y) {
    myLastX = x;
    myLastY = y;
    SceneContext transform = SceneContext.get();
    myScene.mouseDown(transform, (int)myLastX, (int)myLastY);
    repaint();
  }

  /**
   * Simulate releasing the mouse above the given anchor of the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   * @param targetClass the class of target we want to click on
   * @param pos         which target to click on
   */
  public void mouseRelease(String componentId, Class targetClass, int pos) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      List<Target> targets = component.getTargets();
      int n = 0;
      for (Target target : targets) {
        if (targetClass.isInstance(target)) {
          if (pos == n) {
            mouseRelease(target.getCenterX(), target.getCenterY());
            return;
          }
          n++;
        }
      }
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
      AnchorTarget target = ConstraintComponentUtilities.getAnchorTarget(component, type);
      float x = target.getCenterX();
      float y = target.getCenterY();
      mouseRelease(x, y);
    }
  }

  /**
   * Simulate releasing the mouse above the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   */
  public void mouseRelease(String componentId) {
    mouseRelease(componentId, 0, 0);
  }

  /**
   * Simulate releasing the mouse above the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   * @param offsetX     x offset from the center of the component
   * @param offsetY     y offset from the center of the component
   */
  public void mouseRelease(String componentId, float offsetX, float offsetY) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      float x = component.getCenterX() + offsetX;
      float y = component.getCenterY() + offsetY;
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
  public void mouseRelease(float x, float y) {
    // drag first
    int steps = 10;
    float dx = x - myLastX;
    float dy = y - myLastY;
    float deltaX = dx / (float)steps;
    float deltaY = dy / (float)steps;
    dx = myLastX;
    dy = myLastY;
    SceneContext transform = SceneContext.get();
    if (dx != 0 || dy != 0) {
      for (int i = 0; i < steps; i++) {
        myScene.mouseDrag(transform, (int)dx, (int)dy);
        myScene.buildDisplayList(myDisplayList, System.currentTimeMillis());
        dx += deltaX;
        dy += deltaY;
      }
      myScene.mouseDrag(transform, (int)x, (int)y);
    }
    myScene.mouseRelease(transform, (int)x, (int)y);
    repaint();
  }

  public DisplayList getDisplayList() {
    return myDisplayList;
  }

  /**
   * Select the widget corresponding to the componentId
   *
   * @param componentId
   * @param selected
   */
  public void select(String componentId, boolean selected) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      if (selected) {
        myScene.select(Collections.singletonList(component));
      } else {
        myScene.select(Collections.emptyList());
      }
      repaint();
    }
  }

  /**
   * Regenerate the display list
   */
  public void repaint() {
    myDisplayList.clear();
    myScene.buildDisplayList(myDisplayList, System.currentTimeMillis());
  }
}
