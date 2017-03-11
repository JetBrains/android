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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.Notch;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * A SceneComponent represents the bounds of a widget (backed by NlComponent).
 * SceneComponent gave us a a couple of extra compared to NlComponent:
 * <ul>
 * <li>expressed in Dp, not pixels</li>
 * <li>animate layout changes</li>
 * <li>can mix NlComponent from different NlModel in the same tree</li>
 * </ul>
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class SceneComponent {
  @VisibleForTesting public static final int ANIMATION_DURATION = 350; // ms -- the duration of the animation
  public HashMap<String, Object> myCache = new HashMap<>();
  public SceneDecorator myDecorator;
  private boolean myAllowsAutoconnect = true;
  private TargetProvider myTargetProvider;

  public enum DrawState {SUBDUED, NORMAL, HOVER, SELECTED}

  private final Scene myScene;
  private final NlComponent myNlComponent;
  private ArrayList<SceneComponent> myChildren = new ArrayList<>();
  private SceneComponent myParent = null;
  private boolean myIsSelected = false;
  private boolean myDragging = false;
  private boolean myIsModelUpdateAuthorized = true;

  private AnimatedValue myAnimatedDrawX = new AnimatedValue();
  private AnimatedValue myAnimatedDrawY = new AnimatedValue();
  private AnimatedValue myAnimatedDrawWidth = new AnimatedValue();
  private AnimatedValue myAnimatedDrawHeight = new AnimatedValue();

  private DrawState myDrawState = DrawState.NORMAL;

  private ArrayList<Target> myTargets = new ArrayList<>();

  private int myCurrentLeft = 0;
  private int myCurrentTop = 0;
  private int myCurrentRight = 0;
  private int myCurrentBottom = 0;

  private boolean myShowBaseline = false;
  private final boolean myAllowsFixedPosition;

  private Notch.Provider myNotchProvider;

  @AndroidDpCoordinate
  public int getCenterX() {
    return myCurrentLeft + (myCurrentRight - myCurrentLeft) / 2;
  }

  @AndroidDpCoordinate
  public int getCenterY() {
    return myCurrentTop + (myCurrentBottom - myCurrentTop) / 2;
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor & toString
  /////////////////////////////////////////////////////////////////////////////

  protected SceneComponent(@NotNull Scene scene,
                           @NotNull NlComponent component) {
    myScene = scene;
    myNlComponent = component;
    myScene.addComponent(this);
    myDecorator = SceneDecorator.get(component);
    myAllowsAutoconnect = !myNlComponent.getTagName().equalsIgnoreCase(SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE);
    myAllowsFixedPosition = !myNlComponent.getTagName().equalsIgnoreCase(SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE);
  }

  @Override
  public String toString() {
    return getNlComponent().toString();
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
    int duration = ANIMATION_DURATION;
    boolean animating = false;

    /**
     * Set the value directly without animating
     *
     * @param v the value
     */
    public void setValue(@AndroidDpCoordinate int v) {
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

  @Nullable
  public SceneComponent getParent() {
    return myParent;
  }

  public void setParent(@NotNull SceneComponent parent) {
    myParent = parent;
  }

  public boolean allowsAutoConnect() {
    return myScene.isAutoconnectOn() && myAllowsAutoconnect;
  }

  /**
   * Returns the index of the first instance of the given class in the list of targets
   *
   * @param aClass
   * @return
   */
  public int findTarget(Class aClass) {
    int count = myTargets.size();
    for (int i = 0; i < count; i++) {
      if (aClass.isInstance(myTargets.get(i))) {
        return i;
      }
    }
    return -1;
  }

  public void removeTarget(int pos) {
    myTargets.remove(pos);
  }

  /**
   * Returns true if the given candidate is an ancestor of this component
   *
   * @param candidate
   * @return
   */
  public boolean hasAncestor(SceneComponent candidate) {
    SceneComponent parent = getParent();
    while (parent != null) {
      if (parent == candidate) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public boolean allowsFixedPosition() {
    return myAllowsFixedPosition;
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

  /**
   * Get the X coordinate of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   * <p>This is the equivalent of {@link SceneComponent#getDrawX(long 0)}</p>
   */
  public int getDrawX() {
    return myAnimatedDrawX.getValue(0);
  }

  /**
   * Get the Y coordinate of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   * <p>This is the equivalent of {@link SceneComponent#getDrawY(long 0)}</p>
   */
  public int getDrawY() {
    return myAnimatedDrawY.getValue(0);
  }

  /**
   * @return the x offset of this component relative to its parent or the relative to the
   * {@link Scene} if it has no parent.
   */
  public int getOffsetParentX() {
    if (myParent != null) {
      return getDrawX() - myParent.getDrawX();
    }
    return getDrawX();
  }

  /**
   * @return the y offset of this component relative to its parent or the relative to the
   * {@link Scene} if it has no parent.
   */
  public int getOffsetParentY() {
    if (myParent != null) {
      return getDrawY() - myParent.getDrawY();
    }
    return getDrawY();
  }

  /**
   * @return The width of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   */
  public int getDrawWidth() {
    return myAnimatedDrawWidth.getValue(0);
  }

  /**
   * @return The height of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   */
  public int getDrawHeight() {
    return myAnimatedDrawHeight.getValue(0);
  }

  /**
   * Return the X coordinate given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  public int getDrawX(long time) {
    return myAnimatedDrawX.getValue(time);
  }

  /**
   * Return the Y coordinate given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  public int getDrawY(long time) {
    return myAnimatedDrawY.getValue(time);
  }

  /**
   * Return the width given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  public int getDrawWidth(long time) {
    return myAnimatedDrawWidth.getValue(time);
  }

  /**
   * Return the height given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  public int getDrawHeight(long time) {
    return myAnimatedDrawHeight.getValue(time);
  }

  /**
   * Immediately set the position of this {@link SceneComponent} and of the underlying
   * {@link NlComponent}
   *
   * @param dx X coordinate in DP
   * @param dy Y coordinate in DP
   */
  public void setPosition(@AndroidDpCoordinate int dx, @AndroidDpCoordinate int dy) {
    setPosition(dx, dy, false);
  }

  /**
   * Immediately set the position unless the update is coming from
   * a model update and {@link #isModelUpdateAuthorized()} is false.
   *
   * @param dx          X coordinate in DP
   * @param dy          Y coordinate in DP
   * @param isFromModel Notify the that the given coordinates are coming from a model update
   */
  public void setPosition(@AndroidDpCoordinate int dx, @AndroidDpCoordinate int dy, boolean isFromModel) {
    if (!isFromModel || myIsModelUpdateAuthorized) {
      myAnimatedDrawX.setValue(dx);
      myAnimatedDrawY.setValue(dy);
      if(isFromModel) {
        myNlComponent.x = Coordinates.dpToPx(myNlComponent.getModel(), dx);
        myNlComponent.y = Coordinates.dpToPx(myNlComponent.getModel(), dy);
      }
    }
  }

  /**
   * Set the position of this {@link SceneComponent} and begin the animation to the given
   * position at the given time.
   *
   * The position of the underlying NlComponent is set immediately.
   *
   * @param dx          The X position to animate the component to
   * @param dy          The Y position to animate the component to
   * @param time        The time when the animation begins
   * @param isFromModel
   */
  public void setPositionTarget(@AndroidDpCoordinate int dx, @AndroidDpCoordinate int dy, long time, boolean isFromModel) {
    if (!isFromModel || myIsModelUpdateAuthorized) {
      myAnimatedDrawX.setTarget(dx, time);
      myAnimatedDrawY.setTarget(dy, time);
      if(isFromModel) {
        myNlComponent.x = Coordinates.dpToPx(myNlComponent.getModel(), dx);
        myNlComponent.y = Coordinates.dpToPx(myNlComponent.getModel(), dy);
      }
    }
  }

  /**
   * Immediately set the size of this {@link SceneComponent} and of the underlying
   * {@link NlComponent}
   */
  public void setSize(@AndroidDpCoordinate int width, @AndroidDpCoordinate int height, boolean isFromModel) {
    if (!isFromModel || myIsModelUpdateAuthorized) {
      myAnimatedDrawWidth.setValue(width);
      myAnimatedDrawHeight.setValue(height);
      if(isFromModel) {
        myNlComponent.w = Coordinates.dpToPx(myNlComponent.getModel(), width);
        myNlComponent.h = Coordinates.dpToPx(myNlComponent.getModel(), height);
      }
    }
  }

  /**
   * Set the size of this {@link SceneComponent} and begin the animation to the given
   * position at the given time.
   *
   * The size of the underlying NlComponent is set immediately.
   *
   * @param width       The width to animate the component to
   * @param height      The height to animate the component to
   * @param time        The time when the animation begins
   * @param isFromModel
   */
  public void setSizeTarget(@AndroidDpCoordinate int width, @AndroidDpCoordinate int height, long time, boolean isFromModel) {
    if (!isFromModel || myIsModelUpdateAuthorized) {
      myAnimatedDrawWidth.setTarget(width, time);
      myAnimatedDrawHeight.setTarget(height, time);
      if(isFromModel) {
        myNlComponent.w = Coordinates.dpToPx(myNlComponent.getModel(), width);
        myNlComponent.h = Coordinates.dpToPx(myNlComponent.getModel(), height);
      }
    }
  }

  /**
   * @return The underlying NlComponent
   */
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
    if (myIsSelected) {
      myDrawState = DrawState.SELECTED;
    }
  }

  @AndroidDpCoordinate
  public int getBaseline() {
    return Coordinates.pxToDp(myNlComponent.getModel(), myNlComponent.getBaseline());
  }

  public void setSelected(boolean selected) {
    if (!selected || !myIsSelected) {
      myShowBaseline = false;
    }
    myIsSelected = selected;
    if (myIsSelected) {
      myDrawState = DrawState.SELECTED;
    }
    else {
      myDrawState = DrawState.NORMAL;
    }
  }

  public void setDragging(boolean dragging) {
    if (!getNlComponent().isRoot()) {
      myDragging = dragging;
    }
  }

  public boolean isDragging() {
    return myDragging;
  }

  public DrawState getDrawState() {
    return myDrawState;
  }

  public List<Target> getTargets() {
    // myTargets is only modified in the dispatch thread so make sure we do not call this method from other threads.
    assert ApplicationManager.getApplication().isDispatchThread();

    return Collections.unmodifiableList(myTargets);
  }

  public SceneDecorator getDecorator() {
    return myDecorator;
  }

  public Notch.Provider getNotchProvider() {
    return myNotchProvider;
  }

  public void setNotchProvider(Notch.Provider notchProvider) {
    myNotchProvider = notchProvider;
  }

  public void setExpandTargetArea(boolean expandArea) {
    for (Target target : myTargets) {
      target.setExpandSize(expandArea);
    }
    myScene.needsRebuildList();
  }

  @VisibleForTesting
  ResizeBaseTarget getResizeTarget(ResizeBaseTarget.Type type) {
    int count = myTargets.size();
    for (int i = 0; i < count; i++) {
      if (myTargets.get(i) instanceof ResizeBaseTarget) {
        ResizeBaseTarget target = (ResizeBaseTarget)myTargets.get(i);
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

  /**
   * Clear our attributes (delegating the action to our view handler)
   */
  public void clearAttributes() {
    getNlComponent().clearAttributes();
  }

  protected void addTarget(@NotNull Target target) {
    target.setComponent(this);
    myTargets.add(target);
  }

  public void addChild(@NotNull SceneComponent child) {
    child.removeFromParent();
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
    boolean needsRepaint;
    myCurrentLeft = myAnimatedDrawX.getValue(time);
    myCurrentTop = myAnimatedDrawY.getValue(time);
    myCurrentRight = myCurrentLeft + myAnimatedDrawWidth.getValue(time);
    myCurrentBottom = myCurrentTop + myAnimatedDrawHeight.getValue(time);
    needsRepaint = myAnimatedDrawX.animating;
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

  public Rectangle fillRect(@Nullable Rectangle rectangle) {
    if (rectangle == null) {
      rectangle = new Rectangle();
    }
    rectangle.x = myCurrentLeft;
    rectangle.y = myCurrentTop;
    rectangle.width = myCurrentRight - myCurrentLeft;
    rectangle.height = myCurrentBottom - myCurrentTop;
    return rectangle;
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

  public String getComponentClassName() {
    if (myNlComponent.viewInfo == null) {
      return null;
    }
    return myNlComponent.viewInfo.getClassName();
  }

  public boolean containsX(@AndroidDpCoordinate int xDp) {
    return getDrawX() <= xDp && xDp <= getDrawX() + getDrawWidth();
  }

  public boolean containsY(@AndroidDpCoordinate int yDp) {
    return getDrawY() <= yDp && yDp <= getDrawY() + getDrawHeight();
  }

  /**
   * Set the TargetProvider for this component
   *
   * @param targetProvider The target provider to set
   * @param isParent       The SceneComponent is the layout
   */
  public void setTargetProvider(TargetProvider targetProvider, boolean isParent) {
    if (myTargetProvider == targetProvider) {
      return;
    }
    myTargetProvider = targetProvider;
    updateTargets(isParent);
  }

  public void updateTargets(boolean isParent) {
    myTargets.clear();
    if (myTargetProvider != null) {
      myTargetProvider.createTargets(this, isParent).forEach(this::addTarget);
    }
  }

  /**
   * @param modelUpdateAuthorized
   */
  public void setModelUpdateAuthorized(boolean modelUpdateAuthorized) {
    myIsModelUpdateAuthorized = modelUpdateAuthorized;
  }

  /**
   * If false, it indicates that the size and coordinates of this {@link SceneComponent}
   * should not be updated from a model update because they are being updated by something else, like
   * a drag event.
   *
   * @return true is a
   */
  public boolean isModelUpdateAuthorized() {
    return myIsModelUpdateAuthorized;
  }
}
