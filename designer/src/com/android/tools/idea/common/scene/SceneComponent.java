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

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.CommonDragTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private TargetProvider myTargetProvider;
  private ComponentProvider myComponentProvider;
  private HitProvider myHitProvider;

  public enum DrawState {SUBDUED, NORMAL, HOVER, SELECTED, DRAG}

  private final Scene myScene;
  private final NlComponent myNlComponent;
  private CopyOnWriteArrayList<SceneComponent> myChildren = new CopyOnWriteArrayList<>();
  private SceneComponent myParent = null;

  private boolean myIsToolLocked = false;
  private boolean myIsSelected = false;
  private boolean myIsHighlighted = false;
  protected boolean myDragging = false;

  /**
   * When true, then {@link DrawState.SELECTED} will be forced every time {@link myIsSelected} is true.
   * When false, the value of {@link myIsSelected} won't have any influence over {@link myDrawState}.
   */
  private boolean myPrioritizeSelectedDrawState = true;

  private AnimatedValue myAnimatedDrawX = new AnimatedValue();
  private AnimatedValue myAnimatedDrawY = new AnimatedValue();
  private AnimatedValue myAnimatedDrawWidth = new AnimatedValue();
  private AnimatedValue myAnimatedDrawHeight = new AnimatedValue();

  private DrawState myDrawState = DrawState.NORMAL;

  @GuardedBy("myTargets")
  private final ArrayList<Target> myTargets = new ArrayList<>();

  @GuardedBy("myTargets")
  @Nullable private ImmutableList<Target> myCachedTargetList;

  @AndroidDpCoordinate private int myCurrentLeft = 0;
  @AndroidDpCoordinate private int myCurrentTop = 0;
  @AndroidDpCoordinate private int myCurrentRight = 0;
  @AndroidDpCoordinate private int myCurrentBottom = 0;

  private boolean myShowBaseline = false;

  @Nullable private Notch.Provider myNotchProvider = null;

  @AndroidDpCoordinate
  public int getCenterX() {
    return myCurrentLeft + (myCurrentRight - myCurrentLeft) / 2;
  }

  @AndroidDpCoordinate
  public int getCenterY() {
    return myCurrentTop + (myCurrentBottom - myCurrentTop) / 2;
  }

  @Nullable private CommonDragTarget myDragTarget;

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor & toString
  /////////////////////////////////////////////////////////////////////////////

  public SceneComponent(@NotNull Scene scene, @NotNull NlComponent component, @NotNull HitProvider hitProvider) {
    myScene = scene;
    myNlComponent = component;
    myScene.addComponent(this);
    SceneManager manager = scene.getSceneManager();
    myDecorator = manager.getSceneDecoratorFactory().get(component);
    myHitProvider = hitProvider;
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
    @AndroidDpCoordinate int value;
    @AndroidDpCoordinate int target;
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
    public void setTarget(@AndroidDpCoordinate int v, long time) {
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

  /**
   * @return the depth from root to this instance. Return 0 if this instance is root.
   */
  public int getDepth() {
    int depth = 0;
    SceneComponent current = myParent;
    while (current != null) {
      current = current.myParent;
      depth += 1;
    }
    return depth;
  }

  private TargetProvider getTargetProvider() {
    return myTargetProvider;
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
    ImmutableList<Target> targets = getTargets();
    int count = targets.size();
    for (int i = 0; i < count; i++) {
      if (aClass.isInstance(targets.get(i))) {
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

  /**
   * Returns true if the component is supposed to be highlighted.
   */
  public boolean isHighlighted() {
    return myIsHighlighted;
  }

  public boolean canShowBaseline() {
    return myShowBaseline;
  }

  public void setShowBaseline(boolean value) {
    myShowBaseline = value;
  }

  /**
   * Returns true if the widget is parent(0,0) - 0x0
   *
   * @return true if no dimension
   */
  public boolean hasNoDimension() {
    return myAnimatedDrawWidth.value == 0 && myAnimatedDrawHeight.value == 0
           && (myAnimatedDrawX.value == myAnimatedDrawX.target)
           && (myAnimatedDrawY.value == myAnimatedDrawY.target);
  }

  /**
   * Get the X coordinate of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   * <p>This is the equivalent of {@link SceneComponent#getDrawX(long 0)}</p>
   */
  @AndroidDpCoordinate
  public int getDrawX() {
    return myAnimatedDrawX.getValue(0);
  }

  /**
   * Get the Y coordinate of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   * <p>This is the equivalent of {@link SceneComponent#getDrawY(long 0)}</p>
   */
  @AndroidDpCoordinate
  public int getDrawY() {
    return myAnimatedDrawY.getValue(0);
  }

  /**
   * @return The width of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   */
  @AndroidDpCoordinate
  public int getDrawWidth() {
    return myAnimatedDrawWidth.getValue(0);
  }

  /**
   * @return The height of this {@link SceneComponent}. If an animation is running, returns
   * the value at the start of the animation.
   */
  @AndroidDpCoordinate
  public int getDrawHeight() {
    return myAnimatedDrawHeight.getValue(0);
  }

  /**
   * Return the X coordinate given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  @AndroidDpCoordinate
  public int getDrawX(long time) {
    return myAnimatedDrawX.getValue(time);
  }

  /**
   * Return the Y coordinate given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  @AndroidDpCoordinate
  public int getDrawY(long time) {
    return myAnimatedDrawY.getValue(time);
  }

  /**
   * Return the width given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  @AndroidDpCoordinate
  public int getDrawWidth(long time) {
    return myAnimatedDrawWidth.getValue(time);
  }

  /**
   * Return the height given an elapsed time. <br/>
   * If beyond duration, returns the target value,
   * if time or progress is zero, returns the start value.
   */
  @AndroidDpCoordinate
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
    myAnimatedDrawX.setValue(dx);
    myAnimatedDrawY.setValue(dy);
    if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
      NlComponentHelperKt.setX(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), dx));
      NlComponentHelperKt.setY(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), dy));
    }
    myScene.needsRebuildList();
  }

  /**
   * Set the position of this {@link SceneComponent} and begin the animation to the given
   * position at the given time.
   * <p>
   * The position of the underlying NlComponent is set immediately.
   *
   * @param dx          The X position to animate the component to
   * @param dy          The Y position to animate the component to
   * @param time        The time when the animation begins
   */
  public void setPositionTarget(@AndroidDpCoordinate int dx, @AndroidDpCoordinate int dy, long time) {
    myAnimatedDrawX.setTarget(dx, time);
    myAnimatedDrawY.setTarget(dy, time);
    if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
      NlComponentHelperKt.setX(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), dx));
      NlComponentHelperKt.setY(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), dy));
    }
    else {
      myScene.needsRebuildList();
    }
  }

  /**
   * Immediately set the size of this {@link SceneComponent} and of the underlying
   * {@link NlComponent}
   */
  public void setSize(@AndroidDpCoordinate int width, @AndroidDpCoordinate int height) {
    myAnimatedDrawWidth.setValue(width);
    myAnimatedDrawHeight.setValue(height);
    if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
      NlComponentHelperKt.setW(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), width));
      NlComponentHelperKt.setH(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), height));
    }
    myScene.needsRebuildList();
  }

  /**
   * Set the size of this {@link SceneComponent} and begin the animation to the given
   * position at the given time.
   * <p>
   * The size of the underlying NlComponent is set immediately.
   *
   * @param width       The width to animate the component to
   * @param height      The height to animate the component to
   * @param time        The time when the animation begins
   */
  public void setSizeTarget(@AndroidDpCoordinate int width, @AndroidDpCoordinate int height, long time) {
    myAnimatedDrawWidth.setTarget(width, time);
    myAnimatedDrawHeight.setTarget(height, time);
    if (NlComponentHelperKt.getHasNlComponentInfo(myNlComponent)) {
      NlComponentHelperKt.setW(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), width));
      NlComponentHelperKt.setH(myNlComponent, Coordinates.dpToPx(myScene.getSceneManager(), height));
    }
  }

  /**
   * @return The underlying NlComponent, possibly given by a provider
   */
  @NotNull
  public NlComponent getAuthoritativeNlComponent() {
    ComponentProvider provider = myComponentProvider;
    if (provider != null) {
      return provider.getComponent(this);
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

  public void setDrawState(@NotNull DrawState drawState) {
    DrawState oldState = myDrawState;
    myDrawState = drawState;
    if (myIsSelected && myPrioritizeSelectedDrawState) {
      myDrawState = DrawState.SELECTED;
    }
    if (oldState != myDrawState) {
      DecoratorUtilities.setTimeChange(myNlComponent, DecoratorUtilities.VIEW, DecoratorUtilities.mapState(drawState));
    }
  }

  public void setPrioritizeSelectedDrawState(boolean prioritizeSelected) {
    myPrioritizeSelectedDrawState = prioritizeSelected;
    updateDrawStateUsingSelection();
  }

  @AndroidDpCoordinate
  public int getBaseline() {
    return Coordinates.pxToDp(getScene().getSceneManager(), NlComponentHelperKt.getBaseline(myNlComponent));
  }

  public void setHighlighted(boolean highlighted) {
    myIsHighlighted = highlighted;
    setDrawState(DrawState.NORMAL);
  }

  public void setSelected(boolean selected) {
    if (!selected || !myIsSelected) {
      myShowBaseline = false;
    }
    myIsSelected = selected;
    updateDrawStateUsingSelection();

    synchronized (myTargets) {
      myTargets.forEach(it -> it.componentSelectionChanged(selected));
    }
  }

  private void updateDrawStateUsingSelection() {
    if (myIsSelected && myPrioritizeSelectedDrawState) {
      setDrawState(DrawState.SELECTED);
    }
    else if (getDrawState() == DrawState.SELECTED) {
      setDrawState(DrawState.NORMAL);
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
    synchronized (myTargets) {
      // myTargets is only modified in the dispatch thread so make sure we do not call this method from other threads.

      if (myCachedTargetList == null) {
        myCachedTargetList = ImmutableList.copyOf(myTargets);
      }
      return myCachedTargetList;
    }
  }

  public SceneDecorator getDecorator() {
    return myDecorator;
  }

  @Nullable
  public Notch.Provider getNotchProvider() {
    return myNotchProvider;
  }

  public void setNotchProvider(@Nullable Notch.Provider notchProvider) {
    myNotchProvider = notchProvider;
  }

  @VisibleForTesting
  ResizeBaseTarget getResizeTarget(ResizeBaseTarget.Type type) {
    ImmutableList<Target> targets = getTargets();
    int count = targets.size();
    for (int i = 0; i < count; i++) {
      if (targets.get(i) instanceof ResizeBaseTarget) {
        ResizeBaseTarget target = (ResizeBaseTarget)targets.get(i);
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
    for (SceneComponent child : myChildren) {
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
   * @param sceneTransform
   * @param rectangle
   * @return true if intersecting with the rectangle
   */
  public boolean intersects(@NotNull SceneContext sceneTransform, @AndroidDpCoordinate Rectangle rectangle) {
    return myHitProvider.intersects(this, sceneTransform, rectangle);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Maintenance
  /////////////////////////////////////////////////////////////////////////////

  protected void addTarget(@NotNull Target target) {
    target.setComponent(this);
    synchronized (myTargets) {
      myCachedTargetList = null;
      myTargets.add(target);
    }
  }

  public void addChild(@NotNull SceneComponent child) {
    child.removeFromParent();
    child.setParent(this);
    myChildren.add(child);
  }

  public void removeFromParent() {
    SceneComponent parent = myParent;
    if (parent != null) {
      parent.remove(this);
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

    needsRebuildDisplayList |= isAnimating();

    ImmutableList<Target> targets = getTargets();
    int num = targets.size();
    for (int i = 0; i < num; i++) {
      Target target = targets.get(i);
      needsRebuildDisplayList |= target.layout(sceneTransform, myCurrentLeft, myCurrentTop, myCurrentRight, myCurrentBottom);
    }

    for (SceneComponent child : myChildren) {
      needsRebuildDisplayList |= child.layout(sceneTransform, time);
    }
    return needsRebuildDisplayList;
  }

  @VisibleForTesting
  public boolean isAnimating() {
    return myAnimatedDrawX.isAnimating() ||
           myAnimatedDrawY.isAnimating() ||
           myAnimatedDrawWidth.isAnimating() ||
           myAnimatedDrawHeight.isAnimating();
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

  public void addHit(@NotNull SceneContext sceneTransform, @NotNull ScenePicker picker, @JdkConstants.InputEventMask int modifiersEx) {
    if (myIsToolLocked) {
      return; // skip this if hidden
    }

    myHitProvider.addHit(this, sceneTransform, picker);

    ImmutableList<Target> targets = getTargets();
    int num = targets.size();
    for (int i = 0; i < num; i++) {
      Target target = targets.get(i);
      target.addHit(sceneTransform, picker, modifiersEx);
    }
    for (SceneComponent child : myChildren) {
      if (child instanceof TemporarySceneComponent) {
        continue;
      }
      child.addHit(sceneTransform, picker, modifiersEx);
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

  @AndroidDpCoordinate
  @NotNull
  public Rectangle2D.Float fillDrawRect2D(long time, @Nullable @AndroidDpCoordinate Rectangle2D.Float rec) {
    if (rec == null) {
      rec = new Rectangle2D.Float();
    }
    rec.x = getDrawX(time);
    rec.y = getDrawY(time);
    rec.width = getDrawWidth(time);
    rec.height = getDrawHeight(time);
    return rec;
  }

  @Nullable
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
    synchronized (myTargets) {
      myCachedTargetList = null;
      myTargets.clear();
    }

    // update the Targets created by parent's TargetProvider
    SceneComponent parent = myParent;
    if (parent != null) {
      TargetProvider provider = parent.getTargetProvider();
      if (provider != null) {
        provider.createChildTargets(parent, this).forEach(this::addTarget);
      }
    }

    // update the Targets created by myTargetProvider
    TargetProvider provider = myTargetProvider;
    if (provider != null) {
      provider.createTargets(this).forEach(this::addTarget);
    }

    // update the Targets of children
    for (SceneComponent child : getChildren()) {
      child.updateTargets();
    }

    if (StudioFlags.NELE_DRAG_PLACEHOLDER.get()) {
      boolean hasDragTarget;
      synchronized (myTargets) {
        // TODO: http://b/120497918 Remove this when removing flag.
        hasDragTarget = myTargets.removeIf(CommonDragTarget::isSupported);
      }
      if (hasDragTarget && myScene.getRoot() != this) {
        if (myDragTarget == null) {
          // Drag Target is reusable.
          myDragTarget = new CommonDragTarget(this);
        }
        addTarget(myDragTarget);
      }
    }
  }
}
