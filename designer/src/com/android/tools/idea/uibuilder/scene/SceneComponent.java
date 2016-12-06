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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.target.AnchorTarget;
import com.android.tools.idea.uibuilder.scene.target.ResizeTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A SceneComponent represents the bounds of a widget (backed by NlComponent).
 * SceneComponent gave us a a couple of extra compared to NlComponent:
 * <ul>
 * <li>expressed in Dp, not pixels</li>
 * <li>animate layout changes</li>
 * <li>can mix NlComponent from different NlModel in the same tree</li>
 * </ul>
 */
public class SceneComponent {
  public HashMap<String, Object> myCache = new HashMap<>();
  public SceneDecorator myDecorator;

  public ArrayList<Target> getTargets() {
    return myTargets;
  }

  public SceneDecorator getDecorator() {
    return myDecorator;
  }

  public enum DrawState {SUBDUED, NORMAL, HOVER, SELECTED}

  private final Scene myScene;
  private final NlComponent myNlComponent;
  private ArrayList<SceneComponent> myChildren = new ArrayList<>();
  private SceneComponent myParent = null;
  private boolean myIsSelected = false;

  private AnimatedValue myAnimatedDrawX = new AnimatedValue();
  private AnimatedValue myAnimatedDrawY = new AnimatedValue();
  private AnimatedValue myAnimatedDrawWidth = new AnimatedValue();
  private AnimatedValue myAnimatedDrawHeight = new AnimatedValue();

  private DrawState myDrawState = DrawState.NORMAL;

  private ArrayList<Target> myTargets = new ArrayList<>();

  private ViewGroupHandler myViewGroupHandler;

  private int myCurrentLeft = 0;
  private int myCurrentTop = 0;
  private int myCurrentRight = 0;
  private int myCurrentBottom = 0;

  private boolean myShowBaseline = false;

  boolean used = true;

  public int getCenterX() {
    return myCurrentLeft + (myCurrentRight - myCurrentLeft) / 2;
  }

  public int getCenterY() {
    return myCurrentTop + (myCurrentBottom - myCurrentTop) / 2;
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public SceneComponent(@NotNull Scene scene, @NotNull NlComponent component) {
    myScene = scene;
    myNlComponent = component;
    updateFrom(component);
    myScene.addComponent(this);
    myDecorator = SceneDecorator.get(component);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Utility class to encapsulate animatable values
   */
  static class AnimatedValue {
    int value;
    int target;
    long startTime;
    int duration = 350; // ms -- the duration of the animation
    boolean animating = false;

    /**
     * Set the value directly without animating
     *
     * @param v the value
     */
    public void setValue(int v) {
      value = v;
      target = v;
      startTime = 0;
      animating = false;
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
        animating = false;
        return;
      }
      startTime = time;
      target = v;
      animating = true;
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
        animating = false;
        return value;
      }
      float progress = (time - startTime) / (float)duration;
      if (progress > 1) {
        value = target;
        animating = false;
        return value;
      }
      else if (progress <= 0) {
        return value;
      }
      animating = true;
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

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  public SceneComponent getParent() {
    return myParent;
  }

  public void setParent(@NotNull SceneComponent parent) {
    myParent = parent;
  }

  public boolean isSelected() {
    return myIsSelected;
  }

  public boolean canShowBaseline() {
    return myShowBaseline;
  }

  public void setShowBaseline(boolean value) {
    myShowBaseline = value;
  }

  public int getDrawX() {
    return myAnimatedDrawX.getValue(0);
  }

  public int getDrawY() {
    return myAnimatedDrawY.getValue(0);
  }

  public int getOffsetParentX() {
    if (myParent != null) {
      return getDrawX() - myParent.getDrawX();
    }
    return getDrawX();
  }

  public int getOffsetParentY() {
    if (myParent != null) {
      return getDrawY() - myParent.getDrawY();
    }
    return getDrawY();
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

  public void setDrawState(@NotNull DrawState drawState) {
    myDrawState = drawState;
  }

  public int getBaseline() {
    return myScene.pxToDp(myNlComponent.getBaseline());
  }

  public void setSelected(boolean selected) {
    if (!selected || (selected && !myIsSelected)) {
      myShowBaseline = false;
    }
    myIsSelected = selected;
  }

  public DrawState getDrawState() {
    return myDrawState;
  }

  public void setExpandTargetArea(boolean expandArea) {
    int count = myTargets.size();
    for (int i = 0; i < count; i++) {
      Target target = myTargets.get(i);
      if (target instanceof AnchorTarget) {
        AnchorTarget anchor = (AnchorTarget)target;
        anchor.setExpandSize(expandArea);
      }
    }
    myScene.needsRebuildList();
  }

  @VisibleForTesting
  AnchorTarget getAnchorTarget(@NotNull AnchorTarget.Type type) {
    int count = myTargets.size();
    for (int i = 0; i < count; i++) {
      if (myTargets.get(i) instanceof AnchorTarget) {
        AnchorTarget target = (AnchorTarget)myTargets.get(i);
        if (target.getType() == type) {
          return target;
        }
      }
    }
    return null;
  }

  @VisibleForTesting
  ResizeTarget getResizeTarget(ResizeTarget.Type type) {
    int count = myTargets.size();
    for (int i = 0; i < count; i++) {
      if (myTargets.get(i) instanceof ResizeTarget) {
        ResizeTarget target = (ResizeTarget)myTargets.get(i);
        if (target.getType() == type) {
          return target;
        }
      }
    }
    return null;
  }

  @Nullable
  public SceneComponent getSceneComponent(@NotNull String componentId) {
    if (componentId.equalsIgnoreCase(myNlComponent.getId())) {
      return this;
    }
    int count = myChildren.size();
    for (int i = 0; i < count; i++) {
      SceneComponent child = myChildren.get(i);
      SceneComponent found = child.getSceneComponent(componentId);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  public ViewGroupHandler getViewGroupHandler() {
    return myViewGroupHandler;
  }

  public void setViewGroupHandler(@Nullable ViewGroupHandler viewGroupHandler, boolean isParent) {
    if (viewGroupHandler == myViewGroupHandler) {
      return;
    }
    myTargets.clear();
    myViewGroupHandler = viewGroupHandler;
    if (myViewGroupHandler != null) {
      myViewGroupHandler.addTargets(this, isParent);
    }
  }

  /**
   * Returns true if the component intersects with the given rect
   *
   * @param rectangle
   * @return true if intersecting with the rectangle
   */
  public boolean intersects(Rectangle rectangle) {
    Rectangle bounds = new Rectangle();
    fillRect(bounds);
    return rectangle.intersects(bounds);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Maintenance
  /////////////////////////////////////////////////////////////////////////////

  public void addTarget(@NotNull Target target) {
    target.setComponent(this);
    myTargets.add(target);
  }

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

  /**
   * Given a list of components, marks the corresponding SceneComponent as selected
   *
   * @param components
   */
  public void markSelection(List<NlComponent> components) {
    final int count = components.size();
    boolean selected = false;
    for (int i = 0; i < count; i++) {
      NlComponent component = components.get(i);
      if (myNlComponent == component) {
        selected = true;
        break;
      }
    }
    setSelected(selected);
    int childCount = myChildren.size();
    for (int i = 0; i < childCount; i++) {
      SceneComponent child = myChildren.get(i);
      child.markSelection(components);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  public boolean layout(@NotNull SceneContext sceneTransform, long time) {
    boolean needsRepaint = false;
    myCurrentLeft = myAnimatedDrawX.getValue(time);
    myCurrentTop = myAnimatedDrawY.getValue(time);
    myCurrentRight = myCurrentLeft + myAnimatedDrawWidth.getValue(time);
    myCurrentBottom = myCurrentTop + myAnimatedDrawHeight.getValue(time);
    needsRepaint |= myAnimatedDrawX.animating;
    needsRepaint |= myAnimatedDrawY.animating;
    needsRepaint |= myAnimatedDrawWidth.animating;
    needsRepaint |= myAnimatedDrawHeight.animating;
    int num = myTargets.size();
    for (int i = 0; i < num; i++) {
      Target target = myTargets.get(i);
      needsRepaint |= target.layout(sceneTransform, myCurrentLeft, myCurrentTop, myCurrentRight, myCurrentBottom);
    }
    int childCount = myChildren.size();
    for (int i = 0; i < childCount; i++) {
      SceneComponent child = myChildren.get(i);
      needsRepaint |= child.layout(sceneTransform, time);
    }
    return needsRepaint;
  }

  public void fillRect(@NotNull Rectangle rectangle) {
    rectangle.x = myCurrentLeft;
    rectangle.y = myCurrentTop;
    rectangle.width = myCurrentRight - myCurrentLeft;
    rectangle.height = myCurrentBottom - myCurrentTop;
  }

  public void addHit(@NotNull SceneContext sceneTransform, @NotNull ScenePicker picker) {
    if (myDrawState == DrawState.HOVER) {
      myDrawState = DrawState.NORMAL;
    }
    picker.addRect(this, 0, sceneTransform.getSwingX(myCurrentLeft),
                   sceneTransform.getSwingY(myCurrentTop),
                   sceneTransform.getSwingX(myCurrentRight),
                   sceneTransform.getSwingY(myCurrentBottom));
    int num = myTargets.size();
    for (int i = 0; i < num; i++) {
      Target target = myTargets.get(i);
      target.addHit(sceneTransform, picker);
    }
    int childCount = myChildren.size();
    for (int i = 0; i < childCount; i++) {
      SceneComponent child = myChildren.get(i);
      child.addHit(sceneTransform, picker);
    }
  }

  public void buildDisplayList(long time, @NotNull DisplayList list, SceneContext sceneContext) {
    myDecorator.buildList(list, time, sceneContext, this);
    for (SceneComponent child : getChildren()) {
      child.buildDisplayList(time, list, sceneContext);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////

  public void fillDrawRect(long time, Rectangle rec) {
    rec.x = getDrawX(time);
    rec.y = getDrawY(time);
    rec.width = getDrawWidth(time);
    rec.height = getDrawHeight(time);
  }

  public Object getId() {
    return myNlComponent.getId();
  }
}
