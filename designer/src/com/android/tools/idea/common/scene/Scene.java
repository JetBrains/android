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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.resources.LayoutDirection;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.target.ActionTarget;
import com.android.tools.idea.common.scene.target.DragBaseTarget;
import com.android.tools.idea.common.scene.target.LassoTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.naveditor.scene.targets.ActionHandleTarget;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.*;
import com.android.tools.idea.uibuilder.handlers.coordinator.CoordinatorSnapTarget;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.target.*;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.model.SelectionHandle.PIXEL_MARGIN;
import static com.android.tools.idea.uibuilder.model.SelectionHandle.PIXEL_RADIUS;

/**
 * A Scene contains a hierarchy of SceneComponent representing the bounds
 * of the widgets being layed out. Multiple NlModel can be used to populate
 * a Scene.
 * <p>
 * Methods in this class must be called in the dispatch thread.
 */
public class Scene implements SelectionListener, Disposable {

  @SwingCoordinate
  private static final int DRAG_THRESHOLD = JBUI.scale(10);

  private final DesignSurface myDesignSurface;
  private final SceneManager mySceneManager;
  private static final boolean DEBUG = false;
  private final HashMap<NlComponent, SceneComponent> mySceneComponents = new HashMap<>();
  private SceneComponent myRoot;
  private boolean myIsAnimated = true; // animate layout changes

  public static final int NO_LAYOUT = 0;
  public static final int IMMEDIATE_LAYOUT = 1;
  public static final int ANIMATED_LAYOUT = 2;
  private long myDisplayListVersion = 1;
  private Target myOverTarget;
  private Target mySnapTarget;
  private SceneComponent myCurrentComponent;

  private int mNeedsLayout = NO_LAYOUT;

  @AndroidDpCoordinate
  protected int myPressedMouseX;
  @AndroidDpCoordinate
  protected int myPressedMouseY;

  private int myLastMouseX;
  private int myLastMouseY;

  private HitListener myHoverListener = new HitListener();
  private HitListener myHitListener = new HitListener();
  private HitListener myFindListener = new HitListener();
  private HitListener mySnapListener = new HitListener();
  private Target myHitTarget = null;
  private Cursor myMouseCursor;
  private SceneComponent myHitComponent;
  ArrayList<SceneComponent> myNewSelectedComponents = new ArrayList<>();
  private boolean myIsControlDown;
  private boolean myIsShiftDown;
  private boolean myIsAltDown;
  private boolean myShowAllConstraints = false;

  private enum FilterType {ALL, ANCHOR, VERTICAL_ANCHOR, HORIZONTAL_ANCHOR, BASELINE_ANCHOR, NONE, RESIZE}

  private FilterType myFilterTarget = FilterType.NONE;

  public Scene(@NotNull Disposable parentDisposable, @NotNull DesignSurface surface) {
    myDesignSurface = surface;
    mySceneManager = myDesignSurface.getSceneManager();
    myDesignSurface.getSelectionModel().addListener(this);

    Disposer.register(parentDisposable, this);
  }

  @Override
  public void dispose() {
    myDesignSurface.getSelectionModel().removeListener(this);
  }

  @NotNull
  public SceneManager getSceneManager() {
    return mySceneManager;
  }

  public boolean supportsRTL() {
    return true;
  }

  public boolean isInRTL() {
    Configuration configuration = myDesignSurface.getConfiguration();
    if (configuration == null) {
      return false;
    }
    LayoutDirectionQualifier qualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    if (qualifier == null) {
      return false;
    }
    return qualifier.getValue() == LayoutDirection.RTL;
  }

  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Return the current animation status
   *
   * @return true if layout updates will animate
   */
  public boolean isAnimated() {
    return myIsAnimated;
  }

  /**
   * Set the layout updates to animate or not
   *
   * @param animated true to animate the changes
   */
  public void setAnimated(boolean animated) {
    myIsAnimated = animated;
  }

  /**
   * Return the SceneComponent corresponding to the given NlComponent, if existing
   *
   * @param component the NlComponent to use
   * @return the SceneComponent paired to the given NlComponent, if found
   */
  @Nullable
  public SceneComponent getSceneComponent(@Nullable NlComponent component) {
    if (component == null) {
      return null;
    }
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

  public List<NlComponent> getSelection() {
    return myDesignSurface.getSelectionModel().getSelection();
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

  public Cursor getMouseCursor() {
    return myMouseCursor;
  }

  public boolean isAutoconnectOn() {
    return PropertiesComponent.getInstance().getBoolean(ConstraintLayoutHandler.AUTO_CONNECT_PREF_KEY, false);
  }

  public boolean isShowAllConstraints() {
    return myShowAllConstraints || PropertiesComponent.getInstance().getBoolean(ConstraintLayoutHandler.SHOW_CONSTRAINTS_PREF_KEY);
  }

  @VisibleForTesting
  public void setShowAllConstraints(boolean showAllConstraints) {
    myShowAllConstraints = showAllConstraints;
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
   * Clear all constraints on a widget
   */
  public void clearAttributes() {
    if (myRoot != null) {
      myRoot.clearAttributes();
    }
    select(Collections.emptyList());
  }

  /**
   * Add the given SceneComponent to the Scene
   *
   * @param component component to add
   */
  public void addComponent(@NotNull SceneComponent component) {
    mySceneComponents.put(component.getNlComponent(), component);
    needsRebuildList();
  }

  /**
   * Remove teh given SceneComponent from the Scene
   *
   * @param component component to remove
   */
  public void removeComponent(@NotNull SceneComponent component) {
    component.removeFromParent();
    mySceneComponents.remove(component.getNlComponent(), component);
    needsRebuildList();
  }

  void removeAllComponents() {
    for (Iterator<Map.Entry<NlComponent, SceneComponent>> it = mySceneComponents.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<NlComponent, SceneComponent> entry = it.next();
      entry.getValue().removeFromParent();
      it.remove();
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
  //region Painting
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   * @return true if we need to repaint the screen
   */
  public void buildDisplayList(@NotNull DisplayList displayList, long time, SceneView sceneView) {
    buildDisplayList(displayList, time, SceneContext.get(sceneView));
  }

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   * @return true if we need to repaint the screen
   */
  public void buildDisplayList(@NotNull DisplayList displayList, long time) {
    layout(time, SceneContext.get());
    buildDisplayList(displayList, time, SceneContext.get());
  }

  public void repaint() {
    myDesignSurface.repaint();
  }

  /**
   * Paint the current scene into the given display list
   *
   * @param displayList
   * @param time
   */
  public void buildDisplayList(@NotNull DisplayList displayList, long time, SceneContext sceneContext) {
    if (myRoot != null) {
      myRoot.buildDisplayList(time, displayList, sceneContext);
      if (DEBUG) {
        System.out.println("========= DISPLAY LIST ======== \n" + displayList.serialize());
      }
    }
    else {
      if (DEBUG) {
        System.out.println("Scene:Paint() - NO ROOT ");
      }
    }
  }

  /**
   * Layout targets
   *
   * @param time
   * @param sceneContext
   * @return true if we need to repaint the screen
   */
  public boolean layout(long time, SceneContext sceneContext) {
    boolean needsToRebuildDisplayList = false;
    if (myRoot != null) {
      needsToRebuildDisplayList = myRoot.layout(sceneContext, time);
      if (needsToRebuildDisplayList) {
        needsRebuildList();
      }
    }
    return needsToRebuildDisplayList;
  }

  /**
   * Select the given components
   *
   * @param components The components to select
   */
  public void select(List<SceneComponent> components) {
    if (myDesignSurface != null) {
      ArrayList<NlComponent> nlComponents = new ArrayList<>();
      if (myIsShiftDown) {
        List<NlComponent> selection = myDesignSurface.getSelectionModel().getSelection();
        nlComponents.addAll(selection);
      }
      for (SceneComponent sceneComponent : components) {
        NlComponent nlComponent = sceneComponent.getNlComponent();
        if (myIsShiftDown && nlComponents.contains(nlComponent)) {
          // if shift is pressed and the component is already selected, remove it from the selection
          nlComponents.remove(nlComponent);
        }
        else {
          nlComponents.add(nlComponent);
        }
      }
      myDesignSurface.getSelectionModel().setSelection(nlComponents);
    }
  }

  /**
   * Decides which target type we should display
   *
   * @param target
   * @return true if the target will be displayed
   */
  public boolean allowsTarget(Target target) {
    // TODO: this should really be delegated to the handlers
    SceneComponent component = target.getComponent();
    if (component.isSelected()) {
      boolean hasBaselineConnection = component.getAuthoritativeNlComponent().getAttribute(SHERPA_URI,
                                                                                           ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
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
      // if the baseline shows, hide all the targets others than ActionTarget, ConstraintDragTarget and ResizeTarget
      if (component.canShowBaseline()) {
        return (target instanceof ActionTarget) ||
               (target instanceof ConstraintDragTarget) ||
               (target instanceof DragBaseTarget) ||
               (target instanceof ResizeBaseTarget);
      }
      return !component.isDragging();
    }
    if (target instanceof CoordinatorSnapTarget) {
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
    if (myFilterTarget == FilterType.RESIZE && target instanceof ResizeBaseTarget) {
      return true;
    }
    if (target instanceof MultiComponentTarget) {
      return true;
    }
    if (target instanceof DragBaseTarget) {
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
    Target mySkipTarget = null;

    public HitListener() {
      myPicker.setSelectListener(this);
    }

    public void skipTarget(Target target) {
      mySkipTarget = target;
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
        if (mySkipTarget == over) {
          return;
        }
        Target target = (Target)over;
        if (dist < myClosestTargetDistance) {
          myHitTargets.clear();
          myHitTargets.add(target);
          myClosestTargetDistance = dist;
        }
        else if (dist == myClosestTargetDistance) {
          myHitTargets.add(target);
        }
      }
      else if (over instanceof SceneComponent) {
        SceneComponent component = (SceneComponent)over;
        if (dist < myClosestComponentDistance) {
          myHitComponents.clear();
          myHitComponents.add(component);
          myClosestComponentDistance = dist;
        }
        else if (dist == myClosestComponentDistance) {
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
      List<NlComponent> selection = myDesignSurface.getSelectionModel().getSelection();
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
      Target hit = null;
      boolean found = false;
      for (Target target : myHitTargets) {
        if (target == filteredTarget) {
          found = true;
          continue;
        }
        if (filteredTarget.getClass().isAssignableFrom(target.getClass())) {
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
      List<NlComponent> selection = myDesignSurface.getSelectionModel().getSelection();
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
        if (selection.contains(target.getNlComponent())) {
          candidate = target;
          break;
        }
      }
      // We now have the first element in the selection. Let's try again this time...
      for (int i = count - 1; i >= 0; i--) {
        SceneComponent target = myHitComponents.get(i);
        if (target.hasAncestor(candidate)) {
          candidate = target;
          break;
        }
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
    if (myRoot != null) {
      myHoverListener.find(transform, myRoot, x, y);
      mySnapListener.find(transform, myRoot, x, y);
    }
    repaint();
    Target closestTarget = myHoverListener.getClosestTarget();
    if (myOverTarget != closestTarget) {
      if (myOverTarget != null) {
        myOverTarget.setOver(false);
        myOverTarget = null;
        needsRebuildList();
      }
      if (closestTarget != null) {
        closestTarget.setOver(true);
        transform.setToolTip(closestTarget.getToolTipText());
        myOverTarget = closestTarget;
        needsRebuildList();
      }
    }
    if (closestTarget != null) {
      Target snapTarget = myHoverListener.getFilteredTarget(closestTarget);
      if (snapTarget != mySnapTarget) {
        if (mySnapTarget != null) {
          mySnapTarget.setOver(false);
          mySnapTarget = null;
          needsRebuildList();
        }
        if (snapTarget != null) {
          snapTarget.setOver(true);
          transform.setToolTip(closestTarget.getToolTipText());
          mySnapTarget = closestTarget;
          needsRebuildList();
        }
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

    setCursor(transform, x, y);
  }

  private void setCursor(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myMouseCursor = Cursor.getDefaultCursor();
    if (myCurrentComponent != null && myCurrentComponent.isDragging()) {
      myMouseCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
      return;
    }
    if (myOverTarget != null) {
      myMouseCursor = myOverTarget.getMouseCursor();
      return;
    }

    SelectionModel selectionModel = myDesignSurface.getSelectionModel();

    if (!selectionModel.isEmpty()) {
      int max = Coordinates.getAndroidDimensionDip(myDesignSurface, PIXEL_RADIUS + PIXEL_MARGIN);
      SelectionHandle handle = selectionModel.findHandle(x, y, max);
      if (handle != null) {
        myMouseCursor = handle.getCursor();
        return;
      }
    }
    SceneComponent component = findComponent(transform, x, y);
    if (component != null && component.getParent() != null) {
      myMouseCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }
  }

  private void delegateMouseDownToSelection(int x, int y, SceneComponent currentComponent) {
    // update other selected widgets
    java.util.List<NlComponent> selection = getSelection();
    if (selection.size() > 1) {
      for (NlComponent nlComponent : selection) {
        if (nlComponent == currentComponent.getNlComponent()) {
          continue;
        }
        SceneComponent c = currentComponent.getScene().getSceneComponent(nlComponent);
        if (c != null && c != currentComponent) {
          for (Target target : c.getTargets()) {
            if (target instanceof MultiComponentTarget) {
              target.mouseDown(x, y);
            }
          }
        }
      }
    }
  }

  private void delegateMouseDragToSelection(int x, int y, @Nullable Target closestTarget, SceneComponent currentComponent) {
    // update other selected widgets
    java.util.List<NlComponent> selection = getSelection();
    if (selection.size() > 1) {
      for (NlComponent nlComponent : selection) {
        if (nlComponent == currentComponent.getNlComponent()) {
          continue;
        }
        SceneComponent c = currentComponent.getScene().getSceneComponent(nlComponent);
        if (c != null && c != currentComponent) {
          for (Target target : c.getTargets()) {
            if (target instanceof MultiComponentTarget) {
              ArrayList<Target> list = new ArrayList<>();
              list.add(closestTarget);
              target.mouseDrag(x, y, list);
            }
          }
        }
      }
    }
  }

  private void delegateMouseReleaseToSelection(int x, int y, @Nullable Target closestTarget, SceneComponent currentComponent) {
    // update other selected widgets
    java.util.List<NlComponent> selection = getSelection();
    if (selection.size() > 1) {
      int count = selection.size();
      for (int i = 0; i < count; i++) {
        NlComponent nlComponent = selection.get(i);
        if (nlComponent == currentComponent.getNlComponent()) {
          continue;
        }
        SceneComponent c = currentComponent.getScene().getSceneComponent(nlComponent);
        if (c != null) {
          for (Target target : c.getTargets()) {
            if (target instanceof MultiComponentTarget) {
              target.mouseRelease(x, y, Collections.singletonList(closestTarget));
            }
          }
        }
      }
    }
  }

  public void mouseDown(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myPressedMouseX = x;
    myPressedMouseY = y;

    mNeedsLayout = NO_LAYOUT;
    myLastMouseX = x;
    myLastMouseY = y;
    myFilterTarget = FilterType.NONE;
    if (myRoot == null) {
      return;
    }
    myHitListener.find(transform, myRoot, x, y);
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
      if (myHitTarget instanceof MultiComponentTarget) {
        delegateMouseDownToSelection(x, y, myHitTarget.getComponent());
      }
    }
  }

  public void mouseDrag(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myLastMouseX == x && myLastMouseY == y) {
      return;
    }
    myLastMouseX = x;
    myLastMouseY = y;
    if (myHitTarget != null) {
      // if component is not yet being dragged, is not selected and is only moved a tiny bit, then dont do anything as the user is just trying to select it.
      if (!myHitTarget.getComponent().isDragging() &&
          !myDesignSurface.getSelectionModel().isSelected(myHitTarget.getComponent().getNlComponent()) &&
          isWithinThreshold(myPressedMouseX, x, transform) &&
          isWithinThreshold(myPressedMouseY, y, transform)) {
        return;
      }

      myHitListener.skipTarget(myHitTarget);
      myHitListener.find(transform, myRoot, x, y);
      myHitTarget.mouseDrag(x, y, myHitListener.myHitTargets);
      if (myHitTarget instanceof MultiComponentTarget) {
        delegateMouseDragToSelection(x, y, myHitListener.getClosestTarget(), myHitTarget.getComponent());
      }
      myHitListener.skipTarget(null);
    }
    mouseHover(transform, x, y);
    checkRequestLayoutStatus();
  }

  private static boolean isWithinThreshold(@AndroidDpCoordinate int pos1, @AndroidDpCoordinate int pos2, SceneContext transform) {
    @SwingCoordinate int pos3 = transform.getSwingDimension(pos1);
    @SwingCoordinate int pos4 = transform.getSwingDimension(pos2);
    return Math.abs(pos3 - pos4) < DRAG_THRESHOLD;
  }

  public void checkRequestLayoutStatus() {
    if (mNeedsLayout != NO_LAYOUT) {
      SceneManager manager = myDesignSurface.getSceneManager();
      if (StudioFlags.NELE_LIVE_RENDER.get() && manager instanceof LayoutlibSceneManager) {
        ((LayoutlibSceneManager)manager).requestLayoutAndRender(mNeedsLayout == ANIMATED_LAYOUT);
      }
      else if (manager != null) {
        manager.layout(mNeedsLayout == ANIMATED_LAYOUT);
      }
    }
  }

  public void mouseRelease(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;
    if (myHitTarget != null) {
      myHitListener.find(transform, myRoot, x, y);
      Target closest = myHitListener.getFilteredTarget(myHitTarget);
      myHitTarget.mouseRelease(x, y, closest != null ? Collections.singletonList(closest) : Collections.emptyList());
      myHitTarget.getComponent().setDragging(false);
      if (myHitTarget instanceof MultiComponentTarget) {
        delegateMouseReleaseToSelection(x, y, myHitListener.getClosestTarget(), myHitTarget.getComponent());
      }
    }
    myFilterTarget = FilterType.NONE;
    myNewSelectedComponents.clear();
    if (myHitComponent != null && myHitListener.getClosestComponent() == myHitComponent) {
      myNewSelectedComponents.add(myHitComponent);
    }
    if (myHitTarget instanceof ActionTarget
        || myHitTarget instanceof GuidelineTarget
        || myHitTarget instanceof BarrierTarget) {
      // it will be outside the bounds of the component, so will likely have
      // selected a different one...
      myNewSelectedComponents.clear();
      myNewSelectedComponents.add(myHitTarget.getComponent());
    }
    if (myHitTarget instanceof DragBaseTarget) {
      DragBaseTarget dragTarget = (DragBaseTarget)myHitTarget;
      if (dragTarget.hasChangedComponent()) {
        myNewSelectedComponents.clear();
        myNewSelectedComponents.add(dragTarget.getComponent());
      }
    }
    if (myHitTarget instanceof LassoTarget) {
      LassoTarget lassoTarget = (LassoTarget)myHitTarget;
      lassoTarget.fillSelectedComponents(myNewSelectedComponents);
    }
    if (myHitTarget instanceof ActionHandleTarget) {
      // TODO: Refactor this so explicit cast not required
      SceneComponent closestComponent = myHitListener.getClosestComponent();
      if (closestComponent != null) {
        ActionHandleTarget actionHandleTarget = (ActionHandleTarget)myHitTarget;
        actionHandleTarget.createAction(closestComponent);
      }
    }
    boolean canChangeSelection = true;
    if (myHitTarget != null) {
      canChangeSelection = myHitTarget.canChangeSelection();
    }
    if (canChangeSelection && !sameSelection()) {
      select(myNewSelectedComponents);
    }
    checkRequestLayoutStatus();
  }

  private boolean sameSelection() {
    List<NlComponent> currentSelection = myDesignSurface.getSelectionModel().getSelection();
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

  /**
   * Set a flag to notify that the Scene needs to recompute the layout on the nex
   *
   * @param type Type of layout to recompute: {@link #NO_LAYOUT}, {@link #IMMEDIATE_LAYOUT}, {@link #ANIMATED_LAYOUT}
   */
  public void needsLayout(@MagicConstant(intValues = {NO_LAYOUT, IMMEDIATE_LAYOUT, ANIMATED_LAYOUT}) int type) {
    if (mNeedsLayout < type) {
      mNeedsLayout = type;
    }
  }

  public long getDisplayListVersion() {
    return myDisplayListVersion;
  }

  // TODO: reduce visibility? Probably the modified SceneComponents should do this rather than
  // requiring it to be done explicitly by the code that's modifying them.
  public void needsRebuildList() {
    myDisplayListVersion++;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Finds any components that overlap the given rectangle.
   *
   * @param x      The top left x corner defining the selection rectangle.
   * @param y      The top left y corner defining the selection rectangle.
   * @param width  The w of the selection rectangle
   * @param height The h of the selection rectangle
   */
  public List<SceneComponent> findWithin(@AndroidDpCoordinate int x,
                                         @AndroidDpCoordinate int y,
                                         @AndroidDpCoordinate int width,
                                         @AndroidDpCoordinate int height) {
    List<SceneComponent> within = Lists.newArrayList();
    addWithin(within, getRoot(), x, y, width, height);
    return within;
  }

  private static boolean addWithin(@NotNull List<SceneComponent> result,
                                   @NotNull SceneComponent component,
                                   @AndroidDpCoordinate int x,
                                   @AndroidDpCoordinate int y,
                                   @AndroidDpCoordinate int width,
                                   @AndroidDpCoordinate int height) {
    if (component.getDrawX() + component.getDrawWidth() <= x ||
        x + width <= component.getDrawX() ||
        component.getDrawY() + component.getDrawHeight() <= y ||
        y + height <= component.getDrawY()) {
      return false;
    }

    boolean found = false;
    for (SceneComponent child : component.getChildren()) {
      found |= addWithin(result, child, x, y, width, height);
    }
    if (!found) {
      result.add(component);
    }
    return true;
  }

  @Nullable
  public SceneComponent findComponent(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myRoot == null) {
      return null;
    }
    myFindListener.find(transform, myRoot, x, y);
    return myFindListener.getClosestComponent();
  }

  public Collection<SceneComponent> getSceneComponents() {
    return mySceneComponents.values();
  }

  public void setRoot(SceneComponent root) {
    myRoot = root;
  }

  @Nullable
  @AndroidDpCoordinate
  public Dimension measureWrapSize(@NotNull SceneComponent component) {
    return measure(component, (n, namespace, localName) -> {
      // Change attributes to wrap_content
      if (ATTR_LAYOUT_WIDTH.equals(localName) && ANDROID_URI.equals(namespace)) {
        return VALUE_WRAP_CONTENT;
      }
      if (ATTR_LAYOUT_HEIGHT.equals(localName) && ANDROID_URI.equals(namespace)) {
        return VALUE_WRAP_CONTENT;
      }
      return null;
    });
  }

  @Nullable
  @AndroidDpCoordinate
  private Dimension measure(@NotNull SceneComponent component, @Nullable RenderTask.AttributeFilter filter) {
    // TODO: Reuse snapshot!
    NlComponent neleComponent = component.getNlComponent();
    XmlTag tag = neleComponent.getTag();
    if (!tag.isValid()) {
      return null;
    }
    NlModel model = neleComponent.getModel();
    XmlFile xmlFile = model.getFile();
    AndroidFacet facet = model.getFacet();
    RenderService renderService = RenderService.getInstance(facet);
    RenderLogger logger = renderService.createLogger();
    final RenderTask task = renderService.createTask(xmlFile, model.getConfiguration(), logger, null);
    if (task == null) {
      return null;
    }

    ViewInfo viewInfo = task.measureChild(tag, filter);
    if (viewInfo == null) {
      return null;
    }
    viewInfo = RenderService.getSafeBounds(viewInfo);
    return new Dimension(Coordinates.pxToDp(myDesignSurface.getModel(), viewInfo.getRight() - viewInfo.getLeft()),
                         Coordinates.pxToDp(myDesignSurface.getModel(), viewInfo.getBottom() - viewInfo.getTop()));
  }
}
