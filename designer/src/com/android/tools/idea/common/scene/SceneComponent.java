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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

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
  private ComponentProvider myComponentProvider;

  public enum DrawState {SUBDUED, NORMAL, HOVER, SELECTED, DRAG}

  private final Scene myScene;
  private final NlComponent myNlComponent;
  private CopyOnWriteArrayList<SceneComponent> myChildren = new CopyOnWriteArrayList<>();
  private SceneComponent myParent = null;

  private boolean myIsToolLocked = false;
  private boolean myIsSelected = false;
  protected boolean myDragging = false;
  private boolean myIsModelUpdateAuthorized = true;

  private AnimatedValue myAnimatedDrawX = new AnimatedValue();
  private AnimatedValue myAnimatedDrawY = new AnimatedValue();
  private AnimatedValue myAnimatedDrawWidth = new AnimatedValue();
  private AnimatedValue myAnimatedDrawHeight = new AnimatedValue();

  private DrawState myDrawState = DrawState.NORMAL;

  private ArrayList<Target> myTargets = new ArrayList<>();
  @Nullable private ImmutableList<Target> myCachedTargetList;

  @AndroidDpCoordinate private int myCurrentLeft = 0;
  @AndroidDpCoordinate private int myCurrentTop = 0;
  @AndroidDpCoordinate private int myCurrentRight = 0;
  @AndroidDpCoordinate private int myCurrentBottom = 0;

  private boolean myShowBaseline = false;

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

  public SceneComponent(@NotNull Scene scene, @NotNull NlComponent component) {
    myScene = scene;
    myNlComponent = component;
    myScene.addComponent(this);
    SceneManager manager = scene.getSceneManager();
    myDecorator = manager.getSceneDecoratorFactory().get(component);
    myAllowsAutoconnect = !myNlComponent.getTagName().equalsIgnoreCase(SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE);
    setSelected(myScene.getDesignSurface().getSelectionModel().isSelected(component));
  }

  @Override
  public String toString() {
    return getNlComponent().toString() +
           " [ " +
           myCurrentLeft +
           ", " +
           myCurrentTop +
           " - " +
           myCurrentRight +
           ", " +
           myCurrentBottom +
           "]";
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

    /**
     * Set the value directly without animating
     *
     * @param v the value
     */
    public void setValue(@AndroidDpCoordinate int v) {
      value = v;
      target = v;
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
        target = value;
        return value;
      }
      float progress = (time - startTime) / (float)duration;
      if (progress >= 1) {
        value = target;
        return value;
      }
      else if (progress <= 0) {
        return value;
      }
      return (int)(0.5f + EaseInOutInterpolator(progress, value, target));
    }

    public boolean isAnimating() {
      return value != target;
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

  private void setParent(@NotNull SceneComponent parent) {
    myParent = parent;
  }

  private TargetProvider getTargetProvider() {
    return myTargetProvider;
  }

  public boolean allowsAutoConnect() {
    return myScene.isAutoconnectOn() && myAllowsAutoconnect;
  }

  public boolean useRtlAttributes() {
    // TODO: add a check for a tool attribute on the component overwriting this
    return myScene.supportsRTL();
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

  /**
   * Returns true if the given candidate is an ancestor of this component
   *
   * @param candidate
   * @return
   */
  boolean hasAncestor(SceneComponent candidate) {
    SceneComponent parent = getParent();
    while (parent != null) {
      if (parent == candidate) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
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
      if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
        NlComponentHelperKt.setX(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), dx));
        NlComponentHelperKt.setY(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), dy));
      }
      myScene.needsRebuildList();
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
      if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
        NlComponentHelperKt.setX(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), dx));
        NlComponentHelperKt.setY(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), dy));
      }
      else {
        myScene.needsRebuildList();
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
      if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
        NlComponentHelperKt.setW(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), width));
        NlComponentHelperKt.setH(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), height));
      }
      myScene.needsRebuildList();
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
      if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
        NlComponentHelperKt.setW(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), width));
        NlComponentHelperKt.setH(myNlComponent, Coordinates.dpToPx(myScene.getDesignSurface(), height));
      }
    }
  }

  /**
   * @return The underlying NlComponent, possibly given by a provider
   */
  @NotNull
  public NlComponent getAuthoritativeNlComponent() {
    if (myComponentProvider != null) {
      return myComponentProvider.getComponent(this);
    }
    return myNlComponent;
  }

  /**
   * @return The underlying initial NlComponent
   */
  @NotNull
  public NlComponent getNlComponent() {
    return myNlComponent;
  }

  @NotNull
  public Scene getScene() {
    return myScene;
  }

  public List<SceneComponent> getChildren() {
    return myChildren;
  }

  public int getChildCount() {
    return myChildren.size();
  }

  public SceneComponent getChild(int i) {
    return myChildren.get(i);
  }

  public void setToolLocked(boolean locked) {
    myIsToolLocked = locked;
  }

  public boolean isToolLocked() {
    return myIsToolLocked;
  }

  @NotNull
  public Stream<SceneComponent> flatten() {
    return Stream.concat(
      Stream.of(this),
      getChildren().stream().flatMap(SceneComponent::flatten));
  }

  public void setDrawState(@NotNull DrawState drawState) {
    DrawState oldState = myDrawState;
    myDrawState = drawState;
    if (myIsSelected) {
      myDrawState = DrawState.SELECTED;
    }
    if (oldState != myDrawState) {
      DecoratorUtilities.setTimeChange(myNlComponent, DecoratorUtilities.VIEW, DecoratorUtilities.mapState(drawState));
    }
  }

  @AndroidDpCoordinate
  public int getBaseline() {
    return Coordinates.pxToDp(getScene().getDesignSurface(), NlComponentHelperKt.getBaseline(myNlComponent));
  }

  public void setSelected(boolean selected) {
    if (!selected || !myIsSelected) {
      myShowBaseline = false;
    }
    myIsSelected = selected;
    if (myIsSelected) {
      setDrawState(DrawState.SELECTED);
    }
    else {
      setDrawState(DrawState.NORMAL);
    }
    for (Target target : myTargets) {
      target.onComponentSelectionChanged(myIsSelected);
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

  /**
   * Returns a copy of the list containing this component's targets
   */
  public ImmutableList<Target> getTargets() {
    // myTargets is only modified in the dispatch thread so make sure we do not call this method from other threads.
    assert ApplicationManager.getApplication().isDispatchThread();

    if (myCachedTargetList == null) {
      myCachedTargetList = ImmutableList.copyOf(myTargets);
    }
    return myCachedTargetList;
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

  /**
   * Returns the SceneComponent corresponding to the given id
   *
   * @param componentId the given id
   * @return the matching scene component, or null if none found
   */
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
   * Returns the SceneComponent corresponding to the given NlComponent
   *
   * @param nlComponent the given NlComponent
   * @return the matching scene component, or null if none found
   */
  @Nullable
  public SceneComponent getSceneComponent(@NotNull NlComponent nlComponent) {
    if (nlComponent == myNlComponent) {
      return this;
    }
    for (SceneComponent child : getChildren()) {
      SceneComponent found = child.getSceneComponent(nlComponent);
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
  public boolean intersects(@AndroidDpCoordinate Rectangle rectangle) {
    return rectangle.intersects(fillRect(null));
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Maintenance
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Clear our attributes (delegating the action to our view handler)
   */
  public void clearAttributes() {
    NlComponent component = getAuthoritativeNlComponent();
    ViewGroupHandler viewGroupHandler = NlComponentHelperKt.getViewGroupHandler(component);
    viewGroupHandler.clearAttributes(component);
    for (SceneComponent child : getChildren()) {
      viewGroupHandler.clearAttributes(child.getAuthoritativeNlComponent());
    }
  }

  protected void addTarget(@NotNull Target target) {
    target.setComponent(this);
    myCachedTargetList = null;
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
    if (myChildren.remove(component)) {
      component.myParent = null;
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  public boolean layout(@NotNull SceneContext sceneTransform, long time) {
    boolean needsRebuildDisplayList = false;
    int left = myAnimatedDrawX.getValue(time);
    int top = myAnimatedDrawY.getValue(time);
    int right = left + myAnimatedDrawWidth.getValue(time);
    int bottom = top + myAnimatedDrawHeight.getValue(time);

    needsRebuildDisplayList |= (myCurrentLeft != left);
    needsRebuildDisplayList |= (myCurrentTop != top);
    needsRebuildDisplayList |= (myCurrentRight != right);
    needsRebuildDisplayList |= (myCurrentBottom != bottom);

    myCurrentLeft = left;
    myCurrentTop = top;
    myCurrentRight = right;
    myCurrentBottom = bottom;

    boolean animating = false;
    animating |= myAnimatedDrawX.isAnimating();
    animating |= myAnimatedDrawY.isAnimating();
    animating |= myAnimatedDrawWidth.isAnimating();
    animating |= myAnimatedDrawHeight.isAnimating();

    needsRebuildDisplayList |= animating;

    int num = myTargets.size();
    for (int i = 0; i < num; i++) {
      Target target = myTargets.get(i);
      needsRebuildDisplayList |= target.layout(sceneTransform, myCurrentLeft, myCurrentTop, myCurrentRight, myCurrentBottom);
    }
    int childCount = myChildren.size();
    for (int i = 0; i < childCount; i++) {
      SceneComponent child = myChildren.get(i);
      needsRebuildDisplayList |= child.layout(sceneTransform, time);
    }
    return needsRebuildDisplayList;
  }

  @AndroidDpCoordinate
  public Rectangle fillRect(@AndroidDpCoordinate @Nullable Rectangle rectangle) {
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
    if (myIsToolLocked) {
      return; // skip this if hidden
    }
    picker.addRect(this, 0, sceneTransform.getSwingXDip(myCurrentLeft),
                   sceneTransform.getSwingYDip(myCurrentTop),
                   sceneTransform.getSwingXDip(myCurrentRight),
                   sceneTransform.getSwingYDip(myCurrentBottom));
    int num = myTargets.size();
    for (int i = 0; i < num; i++) {
      Target target = myTargets.get(i);
      target.addHit(sceneTransform, picker);
    }
    int childCount = myChildren.size();
    for (int i = 0; i < childCount; i++) {
      SceneComponent child = myChildren.get(i);
      if (child instanceof TemporarySceneComponent) {
        continue;
      }
      child.addHit(sceneTransform, picker);
    }
  }

  public void buildDisplayList(long time, @NotNull DisplayList list, SceneContext sceneContext) {
    myDecorator.buildList(list, time, sceneContext, this);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////

  @AndroidDpCoordinate
  @NotNull
  public Rectangle fillDrawRect(long time, @Nullable @AndroidDpCoordinate Rectangle rec) {
    if (rec == null) {
      rec = new Rectangle();
    }
    rec.x = getDrawX(time);
    rec.y = getDrawY(time);
    rec.width = getDrawWidth(time);
    rec.height = getDrawHeight(time);
    return rec;
  }

  public String getId() {
    return myNlComponent.getId();
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
   */
  public void setTargetProvider(@Nullable TargetProvider targetProvider) {
    if (myTargetProvider == targetProvider) {
      return;
    }
    myTargetProvider = targetProvider;

    updateTargets();
    for (SceneComponent child : getChildren()) {
      child.updateTargets();
    }
  }

  /**
   * Set the ComponentProvider for this component
   *
   * @param provider the component provider
   */
  public void setComponentProvider(@NotNull ComponentProvider provider) {
    myComponentProvider = provider;
  }

  /**
   * Remove existing {@link Target}s and create the {@link Target}s associated with this SceneComponent and its children.<br>
   * The created Targets will save in the {@link #myTargets} in its associated {@link SceneComponent}.
   */
  public void updateTargets() {
    myCachedTargetList = null;
    myTargets.clear();

    // update the Targets created by parent's TargetProvider
    if (myParent != null && myParent.getTargetProvider() != null) {
      myParent.getTargetProvider().createChildTargets(myParent, this).forEach(this::addTarget);
    }

    // update the Targets created by myTargetProvider
    if (myTargetProvider != null) {
      myTargetProvider.createTargets(this).forEach(this::addTarget);
    }

    // update the Targets of children
    for (SceneComponent child : getChildren()) {
      child.updateTargets();
    }
  }

  /**
   * @param modelUpdateAuthorized
   */
  public void setModelUpdateAuthorized(boolean modelUpdateAuthorized) {
    myIsModelUpdateAuthorized = modelUpdateAuthorized;
  }
}
