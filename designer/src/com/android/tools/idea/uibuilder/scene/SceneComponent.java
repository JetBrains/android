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

import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * A SceneComponent represents the bounds of a widget (backed by NlComponent).
 * SceneComponent gave us a a couple of extra compared to NlComponent:
 * <ul>
 *   <li>expressed in Dp, not pixels</li>
 *   <li>animate layout changes</li>
 *   <li>can mix NlComponent from different NlModel in the same tree</li>
 * </ul>
 */
public class SceneComponent {
  private final Scene myScene;
  private final NlComponent myNlComponent;
  private ArrayList<SceneComponent> myChildren = new ArrayList<>();
  private SceneComponent myParent = null;

  private AnimatedValue myAnimatedDrawX = new AnimatedValue();
  private AnimatedValue myAnimatedDrawY = new AnimatedValue();
  private AnimatedValue myAnimatedDrawWidth = new AnimatedValue();
  private AnimatedValue myAnimatedDrawHeight = new AnimatedValue();

  public boolean used = true;

  /////////////////////////////////////////////////////////////////////////////
  // Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Utility class to encapsulate animatable values
   */
  static class AnimatedValue {
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
      return (int)(0.5f + EaseInOutInterpolator(progress, value, target));
    }

    /**
     * Simple ease in / out interpolator
     *
     * @param progress the current progress value from 0 to 1
     * @param begin    the start value
     * @param end      the final value
     * @return the interpolated value corresponding to the given progress
     */
    double EaseInOutInterpolator(double progress, double begin, double end) {
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

  public SceneComponent(@NotNull Scene scene, @NotNull NlComponent component) {
    myScene = scene;
    myNlComponent = component;
    updateFrom(component);
    myScene.addComponent(this);
  }

  /////////////////////////////////////////////////////////////////////////////
  // Accessors
  /////////////////////////////////////////////////////////////////////////////

  public SceneComponent getParent() {
    return myParent;
  }

  public void setParent(@NotNull SceneComponent parent) {
    myParent = parent;
  }

  public int getDrawX() {
    return myAnimatedDrawX.getValue(0);
  }

  public int getDrawY() {
    return myAnimatedDrawY.getValue(0);
  }

  public int getDrawWidth() {
    return myAnimatedDrawWidth.getValue(0);
  }

  public int getDrawHeight() {
    return myAnimatedDrawHeight.getValue(0);
  }

  public int getDrawX(long time) {
    return myAnimatedDrawX.getValue(time);
  }

  public int getDrawY(long time) {
    return myAnimatedDrawY.getValue(time);
  }

  public int getDrawWidth(long time) {
    return myAnimatedDrawWidth.getValue(time);
  }

  public int getDrawHeight(long time) {
    return myAnimatedDrawHeight.getValue(time);
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

  public int getChildCount() {
    return myChildren.size();
  }

  public SceneComponent getChild(int i) {
    return myChildren.get(i);
  }

  /////////////////////////////////////////////////////////////////////////////
  // Maintenance
  /////////////////////////////////////////////////////////////////////////////

  public void addChild(@NotNull SceneComponent child) {
    child.setParent(this);
    myChildren.add(child);
  }

  public void removeFromParent() {
    if (myParent != null) {
      myParent.remove(this);
    }
  }

  private void remove(@NotNull SceneComponent component) {
    myChildren.remove(component);
  }

  /**
   * Update the current bounds given the NlComponent bounds. If the Scene
   * is set to animate, the new bounds will be animated to from the current bounds.
   *
   * @param component the NlComponent to update from
   */
  public void updateFrom(@NotNull NlComponent component) {
    if (myScene.getAnimate()) {
      long time = System.currentTimeMillis();
      myAnimatedDrawX.setTarget(myScene.pxToDp(component.x), time);
      myAnimatedDrawY.setTarget(myScene.pxToDp(component.y), time);
      myAnimatedDrawWidth.setTarget(myScene.pxToDp(component.w), time);
      myAnimatedDrawHeight.setTarget(myScene.pxToDp(component.h), time);
    }
    else {
      myAnimatedDrawX.setValue(myScene.pxToDp(component.x));
      myAnimatedDrawY.setValue(myScene.pxToDp(component.y));
      myAnimatedDrawWidth.setValue(myScene.pxToDp(component.w));
      myAnimatedDrawHeight.setValue(myScene.pxToDp(component.h));
    }
  }
}
