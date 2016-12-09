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
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.target.*;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.configurations.ConfigurationListener.CFG_DEVICE;

/**
 * A Scene contains a hierarchy of SceneComponent representing the bounds
 * of the widgets being layed out. Multiple NlModel can be used to populate
 * a Scene.
 */
public class Scene implements ModelListener, SelectionListener {

  private final ScreenView myScreenView;
  private static final boolean DEBUG = false;
  private NlModel myModel;
  /** The DPI factor can be manually set for testing. Do not auto-update */
  private boolean isManualDpiFactor;
  private float myDpiFactor;
  private HashMap<NlComponent, SceneComponent> mySceneComponents = new HashMap<>();
  private SceneComponent myRoot;
  private boolean myAnimate = true; // animate layout changes

  public static final int NO_LAYOUT = 0;
  public static final int IMMEDIATE_LAYOUT = 1;
  public static final int ANIMATED_LAYOUT = 2;
  private boolean myNeedsDisplayListRebuilt = true;
  private Target myOverTarget;
  private SceneComponent myCurrentComponent;
  private SceneComponent myDnDComponent;

  private int mNeedsLayout = NO_LAYOUT;

  private int myLastMouseX;
  private int myLastMouseY;
  private boolean myDidPreviousRepaint = true;

  private HitListener myHoverListener = new HitListener();
  private HitListener myHitListener = new HitListener();
  private Target myHitTarget = null;
  private int myMouseCursor;
  private SceneComponent myHitComponent;
  ArrayList<SceneComponent> myNewSelectedComponents = new ArrayList<>();
  private boolean myIsControlDown;
  private boolean myIsShiftDown;
  private boolean myIsAltDown;
  private ConfigurationListener myConfigurationListener;

  private enum FilterType {ALL, ANCHOR, VERTICAL_ANCHOR, HORIZONTAL_ANCHOR, BASELINE_ANCHOR, NONE, RESIZE}

  private FilterType myFilterTarget = FilterType.NONE;

  /**
   * Helper static function to create a Scene instance given a NlModel
   *
   * @param model      the NlModel instance used to populate the Scene
   * @param screenView
   * @return a newly initialized Scene instance populated using the given NlModel
   */
  public static Scene createScene(@NotNull NlModel model, @NotNull ScreenView screenView) {
    Scene scene = new Scene(screenView, model.getConfiguration().getDensity().getDpiValue());
    scene.add(model);
    return scene;
  }

  /**
   * Default constructor
   *
   * @param screenView
   * @param dpiFactor
   */
  private Scene(@NotNull ScreenView screenView, float dpiFactor) {
    myScreenView = screenView;
    myScreenView.getSelectionModel().addListener(this);
    myDpiFactor = dpiFactor / 160f;
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Dp / Pixels conversion utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Convert from Android pixels to Dp
   *
   * @param px the pixel amount
   * @return the converted Dp amount
   */
  public int pxToDp(@AndroidCoordinate int px) {
    return (int)(0.5f + px / myDpiFactor);
  }

  /**
   * Convert from Dp to Android pixels
   *
   * @param dp the Dp amount
   * @return the converted Android pixels amount
   */
  public int dpToPx(@AndroidDpCoordinate int dp) {
    return (int)(0.5f + dp * myDpiFactor);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Set the current dpi factor
   *
   * @param dpiFactor
   */
  @VisibleForTesting
  void setDpiFactorOverride(float dpiFactor) {
    isManualDpiFactor = true;
    myDpiFactor = dpiFactor;
  }

  /**
   * Return the current animation status
   *
   * @return true if layout updates will animate
   */
  public boolean getAnimate() {
    return myAnimate;
  }

  /**
   * Set the layout updates to animate or not
   *
   * @param animate true to animate the changes
   */
  public void setAnimate(boolean animate) {
    myAnimate = animate;
  }

  /**
   * Return the SceneComponent corresponding to the given NlComponent, if existing
   *
   * @param component the NlComponent to use
   * @return the SceneComponent paired to the given NlComponent, if found
   */
  @Nullable
  public SceneComponent getSceneComponent(@NotNull NlComponent component) {
    return mySceneComponents.get(component);
  }

  /**
   * Return the SceneComponent corresponding to the given component id, if found
   *
   * @param componentId the component id to look for
   * @return the SceneComponent paired to the given component id, if found
   */
  @Nullable
  public SceneComponent getSceneComponent(@NotNull String componentId) {
    if (myRoot == null) {
      return null;
    }
    return myRoot.getSceneComponent(componentId);
  }

  /**
   * Return the current SceneComponent root in the Scene
   *
   * @return the root SceneComponent
   */
  @Nullable
  public SceneComponent getRoot() {
    return myRoot;
  }

  public int getMouseCursor() {
    return myMouseCursor;
  }

  public void setDnDComponent(NlComponent component) {
    if (myDnDComponent != null) {
      myDnDComponent.removeFromParent();
    }
    if (component != null) {
      myDnDComponent = new SceneComponent(this, component);
      myDnDComponent.addTarget(new DragDndTarget());
      setAnimate(false);
      myDnDComponent.updateFrom(component);
      setAnimate(true);
    }
    else {
      myDnDComponent = null;
    }
    if (myRoot != null && myDnDComponent != null) {
      myRoot.addChild(myDnDComponent);
      needsRebuildList();
    }
  }

  public boolean isAutoconnectOn() {
    return PropertiesComponent.getInstance().getBoolean(ConstraintLayoutHandler.AUTO_CONNECT_PREF_KEY, false);
  }

  /**
   * Update the current key modifiers state
   *
   * @param modifiers
   */
  public void updateModifiers(int modifiers) {
    myIsControlDown = (((modifiers & InputEvent.CTRL_DOWN_MASK) != 0)
                       || ((modifiers & InputEvent.CTRL_MASK) != 0));
    myIsShiftDown = (((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0)
                     || ((modifiers & InputEvent.SHIFT_MASK) != 0));
    myIsAltDown = (((modifiers & InputEvent.ALT_DOWN_MASK) != 0)
                   || ((modifiers & InputEvent.ALT_MASK) != 0));
  }

  public boolean isControlDown() {
    return myIsControlDown;
  }

  public boolean isShiftDown() {
    return myIsShiftDown;
  }

  public boolean isAltDown() {
    return myIsAltDown;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Update / Maintenance of the tree
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Add the NlComponents contained in the given NlModel to the Scene
   *
   * @param model the NlModel to use
   */
  public void add(@NotNull NlModel model) {
    ConfigurationListener listener = (flags) -> {
      if ((flags & CFG_DEVICE) != 0 && !isManualDpiFactor) {
        float newDpiFactor = model.getConfiguration().getDensity().getDpiValue() / 160f;
        if (myDpiFactor != newDpiFactor) {
          // Update from the model to update the dpi
          updateFrom(model);
        }
      }
      return true;
    };
    model.getConfiguration().addListener(listener);
    Disposer.register(model, () -> model.getConfiguration().removeListener(listener));

    List<NlComponent> components = model.getComponents();
    if (components.size() != 0) {
      NlComponent rootComponent = components.get(0).getRoot();
      myAnimate = false;
      myRoot = updateFromComponent(rootComponent);
      myAnimate = true;
      addTargets(myRoot);
    }
    model.addListener(this);
    myModel = model;
    // let's make sure the selection is correct
    if (myScreenView != null) {
      selectionChanged(myScreenView.getSelectionModel(), myScreenView.getSelectionModel().getSelection());
    }
  }

  /**
   * Add the given SceneComponent to the Scene
   *
   * @param component
   */
  public void addComponent(@NotNull SceneComponent component) {
    mySceneComponents.put(component.getNlComponent(), component);
  }

  /**
   * Update the Scene with the components in the given NlModel
   *
   * @param model the NlModel to udpate from
   */
  public void updateFrom(@NotNull NlModel model) {
    List<NlComponent> components = model.getComponents();
    if (components.size() == 0) {
      mySceneComponents.clear();
      myRoot = null;
      return;
    }
    for (SceneComponent component : mySceneComponents.values()) {
      component.used = false;
    }
    if (!isManualDpiFactor) {
      myDpiFactor = model.getConfiguration().getDensity().getDpiValue() / 160f;
    }
    NlComponent rootComponent = components.get(0).getRoot();
    myRoot = updateFromComponent(rootComponent);
    Iterator<SceneComponent> it = mySceneComponents.values().iterator();
    while (it.hasNext()) {
      SceneComponent component = it.next();
      if (!component.used) {
        component.removeFromParent();
        it.remove();
      }
    }
    if (myRoot != null && myScreenView != null && myScreenView.getSelectionModel().isEmpty()) {
      addTargets(myRoot);
    }
  }

  /**
   * Update (and if necessary, create) the SceneComponent paired to the given NlComponent
   *
   * @param component a given NlComponent
   * @return the SceneComponent paired with the given NlComponent
   */
  private SceneComponent updateFromComponent(@NotNull NlComponent component) {
    SceneComponent sceneComponent = mySceneComponents.get(component);
    if (sceneComponent != null) {
      sceneComponent.used = true;
      sceneComponent.updateFrom(component);
      myNeedsDisplayListRebuilt = true;
    }
    else {
      sceneComponent = new SceneComponent(this, component);
    }
    int numChildren = component.getChildCount();
    for (int i = 0; i < numChildren; i++) {
      SceneComponent child = updateFromComponent(component.getChild(i));
      if (child.getParent() != sceneComponent) {
        sceneComponent.addChild(child);
      }
    }
    return sceneComponent;
  }

  /**
   * Add targets to the given component (by asking the associated
   * {@linkplain ViewGroupHandler} to do it)
   *
   * @param component
   */
  void addTargets(@NotNull SceneComponent component) {
    SceneComponent parent = component.getParent();
    if (parent != null) {
      component = parent;
    }
    else {
      component = myRoot;
    }
    ViewHandler handler = component.getNlComponent().getViewHandler();
    if (handler instanceof ViewGroupHandler) {
      ViewGroupHandler viewGroupHandler = (ViewGroupHandler)handler;
      if (component.getViewGroupHandler() != viewGroupHandler) {
        component.setViewGroupHandler(viewGroupHandler, true);
      }
      int childCount = component.getChildCount();
      for (int i = 0; i < childCount; i++) {
        SceneComponent child = component.getChild(i);
        if (child.getViewGroupHandler() != viewGroupHandler) {
          child.setViewGroupHandler(viewGroupHandler, false);
        }
      }
    }
    needsRebuildList();
  }

  void clearChildTargets(SceneComponent component) {
    int count = component.getChildCount();
    component.setViewGroupHandler(null, false);
    for (int i = 0; i < count; i++) {
      SceneComponent child = component.getChild(i);
      child.setViewGroupHandler(null, false);
      clearChildTargets(child);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region SelectionModel listener callback
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    if (myRoot != null) {
      clearChildTargets(myRoot);
      myRoot.markSelection(selection);
      // After a new selection, we need to figure out the context
      if (!selection.isEmpty()) {
        NlComponent primary = selection.get(0);
        SceneComponent component = getSceneComponent(primary);
        if (component != null) {
          addTargets(component);
        }
        else {
          addTargets(myRoot);
        }
      }
      else {
        addTargets(myRoot);
      }
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region NlModel listeners callbacks
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void modelChanged(@NotNull NlModel model) {
    ApplicationManager.getApplication().runReadAction(() -> {
      updateFrom(model);
    });
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    ApplicationManager.getApplication().runReadAction(() -> {
      updateFrom(model);
    });
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    boolean previous = myAnimate;
    myAnimate = animate;
    updateFrom(model);
    myAnimate = previous;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Painting
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   * @return true if we need to repaint the screen
   */
  public boolean buildDisplayList(@NotNull DisplayList displayList, long time, ScreenView screenView) {
    return buildDisplayList(displayList, time, SceneContext.get(screenView));
  }

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   * @return true if we need to repaint the screen
   */
  public boolean buildDisplayList(@NotNull DisplayList displayList, long time) {
    return buildDisplayList(displayList, time, SceneContext.get());
  }

  public void repaint() {
    myScreenView.getSurface().repaint();
  }

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   * @return true if we need to repaint the screen
   */
  public boolean buildDisplayList(@NotNull DisplayList displayList, long time, SceneContext sceneContext) {
    boolean needsRepaint = false;
    if (myRoot != null) {
      needsRepaint = myRoot.layout(sceneContext, time);
      if (sceneContext != null) {
        myRoot.buildDisplayList(time, displayList, sceneContext);
        if (DEBUG) {
          System.out.println("========= DISPLAY LIST ======== \n" + displayList.serialize());
        }
      }
    }
    else {
      if (DEBUG) {
        System.out.println("Scene:Paint() - NO ROOT ");
      }
    }
    if (myDidPreviousRepaint) {
      myDidPreviousRepaint = needsRepaint;
      needsRepaint = true;
    }
    return needsRepaint;
  }

  /**
   * Select the given component
   *
   * @param component
   */
  public void select(List<SceneComponent> components) {
    if (myScreenView != null) {
      ArrayList<NlComponent> nlComponents = new ArrayList<>();
      if (myIsShiftDown) {
        List<NlComponent> selection = myScreenView.getSelectionModel().getSelection();
        nlComponents.addAll(selection);
      }
      int count = components.size();
      for (int i = 0; i < count; i++) {
        NlComponent component = components.get(i).getNlComponent();
        if (myIsShiftDown && nlComponents.contains(component)) {
          // if shift is pressed and the component is already selected, remove it from the selection
          nlComponents.remove(component);
        }
        else {
          nlComponents.add(component);
        }
      }
      myScreenView.getSelectionModel().setSelection(nlComponents);
    }
  }

  /**
   * Decides which target type we should display
   *
   * @param target
   * @return true if the target will be displayed
   */
  public boolean allowsTarget(Target target) {
    SceneComponent component = target.getComponent();
    if (component.isSelected()) {
      boolean hasBaselineConnection = component.getNlComponent().getAttribute(SdkConstants.SHERPA_URI,
                                                                              SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
      if (target instanceof AnchorTarget) {
        AnchorTarget anchor = (AnchorTarget)target;
        if (anchor.getType() == AnchorTarget.Type.BASELINE) {
          // only show baseline anchor as needed
          return component.canShowBaseline() || hasBaselineConnection;
        }
        else {
          // if the baseline is showing, hide the rest of the anchors
          return (!hasBaselineConnection && !component.canShowBaseline())
                 || (hasBaselineConnection && anchor.isHorizontalAnchor());
        }
      }
      // if the baseline shows, hide all the targets others than ActionTarget, DragTarget and ResizeTarget
      if (component.canShowBaseline()) {
        return (target instanceof ActionTarget) || (target instanceof DragTarget) || (target instanceof ResizeTarget);
      }
      return true;
    }
    if (target instanceof AnchorTarget) {
      AnchorTarget anchor = (AnchorTarget)target;
      if (myFilterTarget == FilterType.BASELINE_ANCHOR) {
        return anchor.getType() == AnchorTarget.Type.BASELINE;
      }
      if (myFilterTarget == FilterType.VERTICAL_ANCHOR
          && anchor.isVerticalAnchor()) {
        return true;
      }
      if (myFilterTarget == FilterType.HORIZONTAL_ANCHOR
          && anchor.isHorizontalAnchor()) {
        return true;
      }
      if (myFilterTarget == FilterType.ANCHOR) {
        return true;
      }
    }
    if (myFilterTarget == FilterType.RESIZE && target instanceof ResizeTarget) {
      return true;
    }
    if (target instanceof DragTarget) {
      return true;
    }
    if (target instanceof LassoTarget) {
      return true;
    }
    if (target instanceof GuidelineCycleTarget) {
      return true;
    }
    if (target instanceof ActionTarget) {
      return false;
    }
    if (myFilterTarget == FilterType.ALL) {
      return true;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Hit listener implementation (used for hover / click detection)
   */
  class HitListener implements ScenePicker.HitElementListener {
    private ScenePicker myPicker = new ScenePicker();
    double myClosestComponentDistance = Double.MAX_VALUE;
    double myClosestTargetDistance = Double.MAX_VALUE;
    ArrayList<SceneComponent> myHitComponents = new ArrayList<>();
    ArrayList<Target> myHitTargets = new ArrayList<>();

    public HitListener() {
      myPicker.setSelectListener(this);
    }

    public void find(@NotNull SceneContext transform,
                     @NotNull SceneComponent root,
                     @AndroidDpCoordinate int x,
                     @AndroidDpCoordinate int y) {
      myHitComponents.clear();
      myHitTargets.clear();
      myClosestComponentDistance = Double.MAX_VALUE;
      myClosestTargetDistance = Double.MAX_VALUE;
      myPicker.reset();
      root.addHit(transform, myPicker);
      myPicker.find(transform.getSwingX(x), transform.getSwingY(y));
    }

    @Override
    public void over(Object over, double dist) {
      if (over instanceof Target) {
        Target target = (Target)over;
        if (dist < myClosestTargetDistance) {
          myHitTargets.clear();
          myHitTargets.add(target);
          myClosestTargetDistance = dist;
        } else if (dist == myClosestTargetDistance) {
          myHitTargets.add(target);
        }
      }
      else if (over instanceof SceneComponent) {
        SceneComponent component = (SceneComponent)over;
        if (dist < myClosestComponentDistance) {
          myHitComponents.clear();
          myHitComponents.add(component);
          myClosestComponentDistance = dist;
        } else if (dist == myClosestComponentDistance) {
          myHitComponents.add(component);
        }
      }
    }

    /**
     * Return the "best" target among the list of targets found by the hit detector.
     * If more than one target have been found, we pick the top-level target preferably,
     * unless there's a component selected (in that case, we pick the best top-level target belonging
     * to a component in the selection)
     *
     * @return preferred target
     */
    public Target getClosestTarget() {
      int count = myHitTargets.size();
      if (count == 0) {
        return null;
      }
      if (count == 1) {
        return myHitTargets.get(0);
      }
      List<NlComponent> selection = myScreenView.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        Target candidate = myHitTargets.get(count - 1);
        for (int i = count - 2; i >= 0; i--) {
          Target target = myHitTargets.get(i);
          if (target.getPreferenceLevel() > candidate.getPreferenceLevel()) {
            candidate = target;
          }
        }
        return candidate;
      }
      Target candidate = myHitTargets.get(count - 1);
      boolean inSelection = selection.contains(candidate.getComponent().getNlComponent());

      for (int i = count - 2; i >= 0; i--) {
        Target target = myHitTargets.get(i);
        if (!selection.contains(target.getComponent().getNlComponent())) {
           continue;
        }
        if (!inSelection || target.getPreferenceLevel() > candidate.getPreferenceLevel()) {
          candidate = target;
          inSelection = true;
        }
      }
      return candidate;
    }

    /**
     * Return a target out of the list of hit targets that doesn't
     * include filteredTarget -- unless that's the only choice. The idea is that when dealing with targets,
     * if there's overlap, you don't want to pick on mouseRelease the same one you already clicked on in mouseDown.
     *
     * @param filteredTarget
     * @return the preferred target out of the list
     */
    public Target getFilteredTarget(Target filteredTarget) {
      int count = myHitTargets.size();
      Target hit = null;
      boolean found = false;
      for (int i = 0; i < count; i++) {
        Target target = myHitTargets.get(i);
        if (target == filteredTarget) {
          found = true;
          continue;
        }
        if (target.getClass() == filteredTarget.getClass()){
          hit = target;
        }
      }
      if (hit == null && found) {
        hit = filteredTarget;
      }
      return hit;
    }

    /**
     * We want to get the best component, defined as the top-level one (in the draw order) and
     * a preference to the selected one (in case multiple components overlap)
     *
     * @return the best component to pick
     */
    public SceneComponent getClosestComponent() {
      int count = myHitComponents.size();
      if (count == 0) {
        return null;
      }
      if (count == 1) {
        return myHitComponents.get(0);
      }
      List<NlComponent> selection = myScreenView.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        return myHitComponents.get(count - 1);
      }
      SceneComponent candidate = myHitComponents.get(count - 1);
      boolean inSelection = selection.contains(candidate.getNlComponent());
      if (inSelection) {
        return candidate;
      }

      for (int i = count - 2; i >= 0; i--) {
        SceneComponent target = myHitComponents.get(i);
        if (!selection.contains(target.getNlComponent())) {
          continue;
        }
        candidate = target;
        break;
      }
      return candidate;
    }
  }

  /**
   * Supports hover
   *
   * @param x
   * @param y
   */
  public void mouseHover(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;
    myMouseCursor = Cursor.DEFAULT_CURSOR;
    if (myRoot != null) {
      myHoverListener.find(transform, myRoot, x, y);
    }
    Target closestTarget = myHoverListener.getClosestTarget();
    if (myOverTarget != closestTarget) {
      if (myOverTarget != null) {
        myOverTarget.setOver(false);
        myOverTarget = null;
      }
      if (closestTarget != null) {
        closestTarget.setOver(true);
        myOverTarget = closestTarget;
      }
    }
    SceneComponent closestComponent = myHoverListener.getClosestComponent();
    if (myCurrentComponent != closestComponent) {
      if (myCurrentComponent != null) {
        myCurrentComponent.setDrawState(SceneComponent.DrawState.NORMAL);
        myCurrentComponent = null;
      }
      if (closestComponent != null) {
        closestComponent.setDrawState(SceneComponent.DrawState.HOVER);
        myCurrentComponent = closestComponent;
      }
    }
    if (myOverTarget != null) {
      myMouseCursor = myOverTarget.getMouseCursor();
    }
  }

  public void mouseDown(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    mNeedsLayout = NO_LAYOUT;
    myLastMouseX = x;
    myLastMouseY = y;
    myFilterTarget = FilterType.NONE;
    if (myRoot == null) {
      return;
    }
    myHitListener.find(transform, myRoot, x, y);
    myHitTarget = null;
    myHitTarget = myHitListener.getClosestTarget();
    myHitComponent = myHitListener.getClosestComponent();
    if (myHitTarget != null) {
      if (myHitTarget instanceof AnchorTarget) {
        AnchorTarget anchor = (AnchorTarget)myHitTarget;
        if (anchor.isHorizontalAnchor()) {
          myFilterTarget = FilterType.HORIZONTAL_ANCHOR;
        }
        else {
          myFilterTarget = FilterType.VERTICAL_ANCHOR;
        }
        if (anchor.getType() == AnchorTarget.Type.BASELINE) {
          myFilterTarget = FilterType.BASELINE_ANCHOR;
        }
      }
      myHitTarget.mouseDown(x, y);
    }
  }

  public void mouseDrag(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myLastMouseX == x && myLastMouseY == y) {
      return;
    }
    myLastMouseX = x;
    myLastMouseY = y;
    if (myHitTarget != null) {
      myHitListener.find(transform, myRoot, x, y);
      myHitTarget.mouseDrag(x, y, myHitListener.getClosestTarget());
    }
    mouseHover(transform, x, y);
    if (mNeedsLayout != NO_LAYOUT) {
      myModel.requestLayout(mNeedsLayout == ANIMATED_LAYOUT ? true : false);
    }
  }

  public void mouseRelease(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;
    if (myHitTarget != null) {
      myHitListener.find(transform, myRoot, x, y);
      myHitTarget.mouseRelease(x, y, myHitListener.getFilteredTarget(myHitTarget));
    }
    myFilterTarget = FilterType.NONE;
    myNewSelectedComponents.clear();
    if (myHitComponent != null && myHitListener.getClosestComponent() == myHitComponent) {
      myNewSelectedComponents.add(myHitComponent);
    }
    if (myHitTarget instanceof ActionTarget) {
      // it will be outside the bounds of the component, so will likely have
      // selected a different one...
      myNewSelectedComponents.clear();
      myNewSelectedComponents.add(myHitTarget.getComponent());
    }
    if (myHitTarget instanceof DragTarget) {
      DragTarget dragTarget = (DragTarget)myHitTarget;
      if (dragTarget.hasChangedComponent()) {
        myNewSelectedComponents.add(dragTarget.getComponent());
      }
    }
    if (myHitTarget instanceof LassoTarget) {
      LassoTarget lassoTarget = (LassoTarget)myHitTarget;
      lassoTarget.fillSelectedComponents(myNewSelectedComponents);
    }
    if (!sameSelection()) {
      select(myNewSelectedComponents);
    }
    if (mNeedsLayout != NO_LAYOUT) {
      myModel.requestLayout(mNeedsLayout == ANIMATED_LAYOUT ? true : false);
    }
  }

  private boolean sameSelection() {
    List<NlComponent> currentSelection = myScreenView.getSelectionModel().getSelection();
    if (myNewSelectedComponents.size() == currentSelection.size()) {
      int count = currentSelection.size();
      for (int i = 0; i < count; i++) {
        NlComponent component = currentSelection.get(i);
        SceneComponent sceneComponent = getSceneComponent(component);
        if (!myNewSelectedComponents.contains(sceneComponent)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public void needsLayout(int type) {
    if (mNeedsLayout < type) {
      mNeedsLayout = type;
    }
  }

  public boolean getNeedsDisplayListRebuilt() {
    return myNeedsDisplayListRebuilt;
  }

  public void clearNeedsRebuildList() {
    myNeedsDisplayListRebuilt = false;
  }

  public void needsRebuildList() {
    myNeedsDisplayListRebuilt = true;
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
