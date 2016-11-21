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
package com.android.tools.idea.uibuilder.handlers.scene;

import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * A SceneComponent represents the bounds of a widget (backed by NlComponent).
 * SceneComponent gave us a a couple of extra compared to NlComponent:
 * - expressed in Dp, not pixels
 * - animate layout changes
 * - can mix NlComponent from different NlModel in the same tree
 */
public class SceneComponent {
  private final Scene myScene;
  private final NlComponent myNlComponent;
  private ArrayList<SceneComponent> myChildren = new ArrayList<>();
  private SceneComponent myParent = null;

  private AnimatedValue animatedDrawX = new AnimatedValue();
  private AnimatedValue animatedDrawY = new AnimatedValue();
  private AnimatedValue animatedDrawWidth = new AnimatedValue();
  private AnimatedValue animatedDrawHeight = new AnimatedValue();

  public boolean used = true;

  /////////////////////////////////////////////////////////////////////////////
  // Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Utility class to encapsulate animatable values
   */
  class AnimatedValue {
    int value;
    int target;
    long startTime;
    int duration = 350; // ms -- the duration of the animation

    /**
     * Set the value directly without animating
     *
     * @param v the value
     */
    public void setValue(int v) {
      value = v;
      startTime = 0;
    }

    /**
     * Define a target value which we will animate to, starting
     * from the given time
     *
     * @param v    the target value to reach
     * @param time the start time to animate
     */
    public void setTarget(int v, long time) {
      if (target == v) {
        return;
      }
      startTime = time;
      target = v;
    }

    /**
     * Return the value given an elapsed time. If beyond duration,
     * returns the target value, if time or progress is zero, returns
     * the start value.
     *
     * @param time the elapsed time
     * @return the corresponding value
     */
    public int getValue(long time) {
      if (startTime == 0) {
        return value;
      }
      float progress = (time - startTime) / (float)duration;
      if (progress > 1) {
        value = target;
        return value;
      }
      else if (progress <= 0) {
        return value;
      }
      return (int)(0.5f + EaseInOutinterpolator(progress, value, target));
    }

    /**
     * Simple ease in / out interpolator
     *
     * @param progress the current progress value from 0 to 1
     * @param begin    the start value
     * @param end      the final value
     * @return the interpolated value corresponding to the given progress
     */
    double EaseInOutinterpolator(double progress, double begin, double end) {
      double change = (end - begin) / 2f;
      progress *= 2f;
      if (progress < 1f) {
        return (change * progress * progress + begin);
      }
      progress -= 1f;
      return (-change * (progress * (progress - 2f) - 1f) + begin);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Constructor
  /////////////////////////////////////////////////////////////////////////////

  public SceneComponent(Scene scene, NlComponent component) {
    myScene = scene;
    myNlComponent = component;
    updateFrom(component);
    scene.addComponent(this);
  }

  /////////////////////////////////////////////////////////////////////////////
  // Accessors
  /////////////////////////////////////////////////////////////////////////////

  public SceneComponent getParent() {
    return myParent;
  }

  public void setParent(SceneComponent parent) {
    myParent = parent;
  }

  public int getDrawX() {
    return animatedDrawX.getValue(0);
  }

  public int getDrawY() {
    return animatedDrawY.getValue(0);
  }

  public int getDrawWidth() {
    return animatedDrawWidth.getValue(0);
  }

  public int getDrawHeight() {
    return animatedDrawHeight.getValue(0);
  }

  public int getDrawX(long time) {
    return animatedDrawX.getValue(time);
  }

  public int getDrawY(long time) {
    return animatedDrawY.getValue(time);
  }

  public int getDrawWidth(long time) {
    return animatedDrawWidth.getValue(time);
  }

  public int getDrawHeight(long time) {
    return animatedDrawHeight.getValue(time);
  }

  @NotNull
  public NlComponent getNlComponent() {
    return myNlComponent;
  }

  @NotNull
  public Scene getScene() {
    return myScene;
  }

  public ArrayList<SceneComponent> getChildren() {
    return myChildren;
  }

  /////////////////////////////////////////////////////////////////////////////
  // Maintenance
  /////////////////////////////////////////////////////////////////////////////

  public void addChild(SceneComponent child) {
    child.setParent(this);
    myChildren.add(child);
  }

  public void removeFromParent() {
    if (myParent != null) {
      myParent.remove(this);
    }
  }

  private void remove(SceneComponent component) {
    myChildren.remove(component);
  }

  /**
   * Update the current bounds given the NlComponent bounds. If the Scene
   * is set to animate, the new bounds will be animated to from the current bounds.
   *
   * @param component the NlComponent to update from
   */
  public void updateFrom(NlComponent component) {
    long time = System.currentTimeMillis();
    if (myScene.getAnimate()) {
      animatedDrawX.setTarget(myScene.pxToDp(component.x), time);
      animatedDrawY.setTarget(myScene.pxToDp(component.y), time);
      animatedDrawWidth.setTarget(myScene.pxToDp(component.w), time);
      animatedDrawHeight.setTarget(myScene.pxToDp(component.h), time);
    }
    else {
      animatedDrawX.setValue(myScene.pxToDp(component.x));
      animatedDrawY.setValue(myScene.pxToDp(component.y));
      animatedDrawWidth.setValue(myScene.pxToDp(component.w));
      animatedDrawHeight.setValue(myScene.pxToDp(component.h));
    }
  }
}
