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
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.target.*;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * A Scene contains a hierarchy of SceneComponent representing the bounds
 * of the widgets being layed out. Multiple NlModel can be used to populate
 * a Scene.
 */
public class Scene implements ModelListener, SelectionListener {

  private final ScreenView myScreenView;
  private static final boolean DEBUG = false;
  private NlModel myModel;
  private float myDpiFactor;
  private HashMap<NlComponent, SceneComponent> mySceneComponents = new HashMap<>();
  private SceneComponent myRoot;
  private boolean myAnimate = true; // animate layout changes

  public static final int NO_LAYOUT = 0;
  public static final int IMMEDIATE_LAYOUT = 1;
  public static final int ANIMATED_LAYOUT = 2;

  private int mNeedsLayout = NO_LAYOUT;

  private int myLastMouseX;
  private int myLastMouseY;
  private boolean myDidPreviousRepaint = true;

  private HitListener myHoverListener = new HitListener();
  private HitListener myHitListener = new HitListener();
  private Target mHitTarget = null;
  private int myMouseCursor;

  public int getMouseCursor() {
    return myMouseCursor;
  }

  private enum FilterType {ALL, ANCHOR, VERTICAL_ANCHOR, HORIZONTAL_ANCHOR, BASELINE_ANCHOR, NONE, RESIZE}

  private FilterType myFilterTarget = FilterType.NONE;

  /**
   * Helper static function to create a Scene instance given a NlModel
   *
   * @param model      the NlModel instance used to populate the Scene
   * @param screenView
   * @return a newly initialized Scene instance populated using the given NlModel
   */
  public static Scene createScene(@NotNull NlModel model, ScreenView screenView) {
    int dpiFactor = model.getConfiguration().getDensity().getDpiValue();
    Scene scene = new Scene(screenView, dpiFactor / 160f);
    scene.add(model);
    return scene;
  }

  /**
   * Default constructor
   *
   * @param screenView
   * @param dpiFactor
   */
  @VisibleForTesting
  Scene(ScreenView screenView, float dpiFactor) {
    myScreenView = screenView;
    if (myScreenView != null) {
      myScreenView.getSelectionModel().addListener(this);
    }
    myDpiFactor = dpiFactor;
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
  public void setDpiFactor(float dpiFactor) {
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
    List<NlComponent> components = model.getComponents();
    if (components.size() == 0) {
      return;
    }
    NlComponent rootComponent = components.get(0).getRoot();
    myAnimate = false;
    myRoot = updateFromComponent(rootComponent);
    myAnimate = true;

    addTargets(myRoot);
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
    if (myRoot != null) {
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
  private void addTargets(@NotNull SceneComponent component) {
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
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region SelectionModel listener callback
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    if (myRoot != null) {
      myRoot.markSelection(selection);
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
  public boolean paint(@NotNull DisplayList displayList, long time, ScreenView screenView) {
    return paint(displayList, time, SceneTransform.get(screenView));
  }

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   * @return true if we need to repaint the screen
   */
  public boolean paint(@NotNull DisplayList displayList, long time) {
    return paint(displayList, time, SceneTransform.get());
  }

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   * @return true if we need to repaint the screen
   */
  public boolean paint(@NotNull DisplayList displayList, long time, SceneTransform sceneTransform) {
    boolean needsRepaint = false;
    if (myRoot != null) {
      needsRepaint = myRoot.layout(time);
      if (sceneTransform != null) {
        myRoot.buildDisplayList(time, displayList, sceneTransform);
        if (DEBUG) {
          System.out.println("========= DISPLAY LIST ======== \n" + displayList.serialize());
        }
      }
    }
    else {
      System.out.println("Scene:Paint() - NO ROOT ");
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
  public void select(SceneComponent component) {
    if (myScreenView != null) {
      myScreenView.getSelectionModel().clear();
      myScreenView.getSelectionModel().setSelection(Collections.singletonList(component.getNlComponent()));
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
      if (target instanceof AnchorTarget) {
        AnchorTarget anchor = (AnchorTarget)target;
        if (anchor.getType() == AnchorTarget.Type.BASELINE) {
          // only show baseline anchor as needed
          return component.canShowBaseline();
        } else {
          // if the baseline is showing, hide the rest of the anchors
          return !component.canShowBaseline();
        }
      }
      // if the baseline shows, hide all the targets others than ActionTarget or DragTarget
      if (component.canShowBaseline()) {
        return (target instanceof ActionTarget) || (target instanceof DragTarget);
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
    SceneComponent myClosestComponent;
    double myClosestComponentDistance = Double.MAX_VALUE;
    Target myClosestTarget;
    double myClosestTargetDistance = Double.MAX_VALUE;
    int myClosestTargetLevel = 0;

    public HitListener() {
      myPicker.setSelectListener(this);
    }

    public void find(@NotNull SceneComponent root, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
      myClosestComponent = null;
      myClosestTarget = null;
      myClosestComponentDistance = Double.MAX_VALUE;
      myClosestTargetDistance = Double.MAX_VALUE;
      myClosestTargetLevel = -1;
      myPicker.reset();
      root.addHit(myPicker);
      myPicker.find(x, y);
    }

    @Override
    public void over(Object over, double dist) {
      if (over instanceof Target) {
        Target target = (Target)over;
        if (dist <= myClosestTargetDistance && target.getPreferenceLevel() >= myClosestTargetLevel) {
          myClosestTargetDistance = dist;
          myClosestTarget = target;
          myClosestTargetLevel = target.getPreferenceLevel();
        }
      }
      else if (over instanceof SceneComponent) {
        SceneComponent component = (SceneComponent)over;
        if (dist < myClosestComponentDistance) {
          myClosestComponentDistance = dist;
          myClosestComponent = component;
        }
      }
    }
  }

  /**
   * Supports hover
   *
   * @param x
   * @param y
   */
  public void mouseHover(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;
    myMouseCursor = Cursor.DEFAULT_CURSOR;
    if (myRoot != null) {
      myHoverListener.find(myRoot, x, y);
    }
    if (myHoverListener.myClosestTarget != null) {
      myHoverListener.myClosestTarget.setOver(true);
      myMouseCursor = myHoverListener.myClosestTarget.getMouseCursor();
    }
    if (myHoverListener.myClosestComponent != null) {
      myHoverListener.myClosestComponent.setDrawState(SceneComponent.DrawState.HOVER);
    }
  }

  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    mNeedsLayout = NO_LAYOUT;
    myLastMouseX = x;
    myLastMouseY = y;
    myFilterTarget = FilterType.NONE;
    if (myRoot == null) {
      return;
    }
    myHitListener.find(myRoot, x, y);
    mHitTarget = myHitListener.myClosestTarget;
    if (mHitTarget != null) {
      if (mHitTarget instanceof AnchorTarget) {
        AnchorTarget anchor = (AnchorTarget)mHitTarget;
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
      mHitTarget.mouseDown(x, y);
    }
  }

  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;
    if (mHitTarget != null) {
      myHitListener.find(myRoot, x, y);
      mHitTarget.mouseDrag(x, y, myHitListener.myClosestTarget);
    }
    mouseHover(x, y);
    if (mNeedsLayout != NO_LAYOUT) {
      myModel.requestLayout(mNeedsLayout == ANIMATED_LAYOUT ? true : false);
    }
  }

  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;
    if (mHitTarget != null) {
      myHitListener.find(myRoot, x, y);
      mHitTarget.mouseRelease(x, y, myHitListener.myClosestTarget);
    }
    myFilterTarget = FilterType.NONE;
    if (mNeedsLayout != NO_LAYOUT) {
      myModel.requestLayout(mNeedsLayout == ANIMATED_LAYOUT ? true : false);
    }
  }

  public void needsLayout(int type) {
    if (mNeedsLayout < type) {
      mNeedsLayout = type;
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
