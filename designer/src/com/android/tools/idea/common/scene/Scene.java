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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.resources.LayoutDirection;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.LassoTarget;
import com.android.tools.idea.common.scene.target.MultiComponentTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.SecondarySelector;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutDecorator;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.JBUI;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Scene contains a hierarchy of SceneComponent representing the bounds
 * of the widgets being layed out. Multiple NlModel can be used to populate
 * a Scene.
 * <p>
 * Methods in this class must be called in the dispatch thread.
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
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
  NlComponent myLastHoverConstraintComponent = null;

  private int mNeedsLayout = NO_LAYOUT;

  @AndroidDpCoordinate protected int myPressedMouseX;
  @AndroidDpCoordinate protected int myPressedMouseY;
  @AndroidDpCoordinate private int myLastMouseX;
  @AndroidDpCoordinate private int myLastMouseY;

  @NotNull private final SceneHitListener myHoverListener;
  @NotNull private final SceneHitListener myHitListener;
  @NotNull private final SceneHitListener myFindListener;
  @NotNull private final SceneHitListener mySnapListener;
  @Nullable private Target myHitTarget = null;
  private Cursor myMouseCursor;
  private SceneComponent myHitComponent;
  List<SceneComponent> myNewSelectedComponentsOnRelease = new ArrayList<>();
  List<SceneComponent> myNewSelectedComponentsOnDown = new ArrayList<>();
  Set<SceneComponent> myHoveredComponents = new HashSet<>();
  private boolean myIsCtrlMetaDown;
  private boolean myIsShiftDown;
  private boolean myIsAltDown;

  private boolean myIsLiveRenderingEnabled;

  public enum FilterType {ALL, ANCHOR, VERTICAL_ANCHOR, HORIZONTAL_ANCHOR, BASELINE_ANCHOR, NONE, RESIZE}

  @NotNull private FilterType myFilterType = FilterType.NONE;

  public Scene(@NotNull SceneManager sceneManager, @NotNull DesignSurface surface, @NotNull RenderSettings renderSettings) {
    myDesignSurface = surface;
    mySceneManager = sceneManager;

    SelectionModel selectionModel = myDesignSurface.getSelectionModel();
    myHoverListener = new SceneHitListener(selectionModel);
    myHitListener = new SceneHitListener(selectionModel);
    myFindListener = new SceneHitListener(selectionModel);
    mySnapListener = new SceneHitListener(selectionModel);
    selectionModel.addListener(this);

    myHoverListener.setTargetFilter(target -> {
      if (target instanceof AnchorTarget) {
        AnchorTarget anchorTarget = (AnchorTarget)target;
        if (myHitTarget == null) {
          // Not interacting with any Target, avoid to hover to edge AnchorTarget.
          return !anchorTarget.isEdge();
        }
        else if (myHitTarget instanceof AnchorTarget) {
          // Interacting with AnchorTarget, only hovers on connectible AnchorTargets.
          return ((AnchorTarget)myHitTarget).isConnectible(anchorTarget);
        }
      }
      return true;
    });

    myIsLiveRenderingEnabled = renderSettings.getUseLiveRendering();

    Disposer.register(sceneManager, this);
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

  public int getRenderedApiLevel() {
    Configuration configuration = myDesignSurface.getConfiguration();
    if (configuration != null) {
      IAndroidTarget target = configuration.getTarget();
      if (target != null) {
        return target.getVersion().getApiLevel();
      }
    }
    return AndroidVersion.VersionCodes.BASE;
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

  @NotNull
  public DesignSurface getDesignSurface() {
    return myDesignSurface;
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

  @Nullable
  public Object getSecondarySelection() {
    return myDesignSurface.getSelectionModel().getSecondarySelection();
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

  /**
   * Update the current key modifiers state
   *
   * @param modifiers
   */
  public void updateModifiers(int modifiers) {
    int ctrlMetaDownMask = AdtUiUtils.getActionMask();
    int ctrlMetaMask = SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK;
    myIsCtrlMetaDown = (((modifiers & ctrlMetaDownMask) != 0)
                        || ((modifiers & ctrlMetaMask) != 0));
    myIsShiftDown = (((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0)
                     || ((modifiers & InputEvent.SHIFT_MASK) != 0));
    myIsAltDown = (((modifiers & InputEvent.ALT_DOWN_MASK) != 0)
                   || ((modifiers & InputEvent.ALT_MASK) != 0));
  }

  public boolean isCtrlMetaDown() {
    return myIsCtrlMetaDown;
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
   * Clear all constraints on a scene
   */
  public void clearAllConstraints() {
    if (myRoot != null) {
      Stream.of(myRoot)
        .flatMap(c -> c.getChildren().stream())
        .forEach(c -> c.clearAttributes());
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
      markSelection(myRoot, model);
    }
  }

  /**
   * Given a {@link SelectionModel}, marks the corresponding SceneComponent as selected.
   */
  private static void markSelection(SceneComponent component, SelectionModel model) {
    component.setSelected(model.isSelected(component.getNlComponent()));

    for (SceneComponent child : component.getChildren()) {
      markSelection(child, model);
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
      // clear the objects and release
      sceneContext.getScenePicker().foreachObject(o -> {
        if (o instanceof SecondarySelector) {
          ((SecondarySelector)o).release();
        }
      });

      sceneContext.getScenePicker().reset();
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
      if (myIsShiftDown || myIsCtrlMetaDown) {
        List<NlComponent> selection = myDesignSurface.getSelectionModel().getSelection();
        nlComponents.addAll(selection);
      }
      for (SceneComponent sceneComponent : components) {
        NlComponent nlComponent = sceneComponent.getNlComponent();
        if ((myIsShiftDown || myIsCtrlMetaDown) && nlComponents.contains(nlComponent)) {
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

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Supports hover
   *
   * @param x
   * @param y
   */
  public void mouseHover(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;
    if (myLastHoverConstraintComponent != null) { // clear hover constraint
      myLastHoverConstraintComponent.putClientProperty(ConstraintLayoutDecorator.CONSTRAINT_HOVER, null);
      myLastHoverConstraintComponent = null;
      needsRebuildList();
    }
    if (myRoot != null) {
      myHoverListener.find(transform, myRoot, x, y);
      mySnapListener.find(transform, myRoot, x, y);
    }
    repaint();
    Target closestTarget = myHoverListener.getClosestTarget();
    String tooltip = null;
    if (myOverTarget != closestTarget) {
      if (myOverTarget != null) {
        myOverTarget.setMouseHovered(false);
        myOverTarget = null;
        needsRebuildList();
      }
      if (closestTarget != null) {
        closestTarget.setMouseHovered(true);
        myOverTarget = closestTarget;
        needsRebuildList();
      }
    }
    if (closestTarget != null) {
      tooltip = closestTarget.getToolTipText();
      Target snapTarget = myHoverListener.getFilteredTarget(closestTarget);
      if (snapTarget != mySnapTarget) {
        if (mySnapTarget != null) {
          mySnapTarget.setMouseHovered(false);
          mySnapTarget = null;
          needsRebuildList();
        }
        if (snapTarget != null) {
          snapTarget.setMouseHovered(true);
          mySnapTarget = closestTarget;
          needsRebuildList();
        }
      }
    }

    updateHoveredComponentsDrawState();

    SceneComponent closestComponent = myHoverListener.getClosestComponent();
    if (closestComponent != null && tooltip == null) {
      tooltip = closestComponent.getNlComponent().getTooltipText();
    }
    if (myCurrentComponent != closestComponent) {
      if (myCurrentComponent != null) {
        myCurrentComponent.setDrawState(SceneComponent.DrawState.NORMAL);
        myCurrentComponent = null;
      }
      if (closestComponent != null) {
        closestComponent.setDrawState(SceneComponent.DrawState.HOVER);
        myCurrentComponent = closestComponent;
      }
      needsRebuildList();
    }

    if (closestComponent == null
        || closestComponent.getNlComponent().isRoot()
           && myOverTarget == null) {
      Object obj = transform.findClickedGraphics(transform.getSwingXDip(x), transform.getSwingYDip(y));
      if (obj != null && obj instanceof SecondarySelector) {
        SecondarySelector ss = (SecondarySelector)obj;
        NlComponent component = ss.getComponent();
        myLastHoverConstraintComponent = ss.getComponent();
        tooltip = getConstraintToolTip(ss);
        component.putClientProperty(ConstraintLayoutDecorator.CONSTRAINT_HOVER, ss.getConstraint());
        needsRebuildList();
      }
    }

    transform.setToolTip(tooltip);
    setCursor(transform, x, y);
  }

  @NotNull
  private String getConstraintToolTip(@NotNull SecondarySelector ss) {
    NlComponent component = ss.getComponent();
    String tooltip;
    String connect = "", target = "";
    SecondarySelector.Constraint connection = ss.getConstraint();
    if (isInRTL()) {
      if (connection == SecondarySelector.Constraint.LEFT) {
        connection = SecondarySelector.Constraint.RIGHT;
      } else  if (connection == SecondarySelector.Constraint.RIGHT) {
        connection = SecondarySelector.Constraint.LEFT;
      }
    }
    switch (connection) {

      case LEFT:
        connect = SdkConstants.ATTR_LAYOUT_START_TO_START_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        connect = SdkConstants.ATTR_LAYOUT_START_TO_END_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        break;
      case RIGHT:
        connect = SdkConstants.ATTR_LAYOUT_END_TO_START_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        connect = SdkConstants.ATTR_LAYOUT_END_TO_END_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        break;
      case TOP:
        connect = SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        connect = SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        break;
      case BOTTOM:
        connect = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        connect = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        if (target != null) {
          break;
        }
        break;
      case BASELINE:
        connect = SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
        target = component.getAttribute(SdkConstants.SHERPA_URI, connect);
        break;
    }
    try {
      connect = connect.substring("layout_constraint".length(), connect.length() - 2).replace("_to", " to ").toLowerCase();
      target = target.substring(target.indexOf("/") + 1);
      tooltip = component.getTooltipText() + " " + connect + " of " + target;
    }
    catch (Exception ex) {
      tooltip = "";
    }
    return tooltip;
  }

  /** Updates the draw state of components being hovered. */
  private void updateHoveredComponentsDrawState() {
    List<SceneComponent> hitComponents = myHoverListener.getHitComponents();
    // Update draw state for currently hovered components.
    for (SceneComponent component : hitComponents) {
      // Skip closest/current component. Not handled here.
      if (component != myCurrentComponent ) {
        NlComponent nlComponent = component.getAuthoritativeNlComponent();
        if (DecoratorUtilities.getTryingToConnectState(nlComponent) != null) {
          // Draw as hovered when creating constraints.
          component.setDrawState(SceneComponent.DrawState.HOVER);
        }
        else {
          component.setDrawState(SceneComponent.DrawState.NORMAL);
        }
      }
      myHoveredComponents.remove(component);
    }

    Iterator<SceneComponent> iterator = myHoveredComponents.iterator();
    // Components not being hovered anymore are set to normal.
    while(iterator.hasNext()) {
      SceneComponent component = iterator.next();
      component.setDrawState(SceneComponent.DrawState.NORMAL);
      iterator.remove();
    }

    // Keep current hovered components.
    myHoveredComponents.addAll(hitComponents);
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

    SceneComponent component = findComponent(transform, x, y);
    if (component != null && component.getParent() != null) {
      myMouseCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }
  }

  private void delegateMouseDownToSelection(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, SceneComponent currentComponent) {
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

  private void delegateMouseDragToSelection(@AndroidDpCoordinate int x,
                                            @AndroidDpCoordinate int y,
                                            @Nullable Target closestTarget,
                                            @NotNull SceneComponent currentComponent) {
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

  private void delegateMouseReleaseToSelection(@AndroidDpCoordinate int x,
                                               @AndroidDpCoordinate int y,
                                               @Nullable Target closestTarget,
                                               @NotNull SceneComponent currentComponent) {
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

  private void delegateMouseCancelToSelection(@NotNull SceneComponent currentComponent) {
    // update other selected widgets
    NlComponent currentNlComponent = currentComponent.getNlComponent();
    Scene scene = currentComponent.getScene();
    List<SceneComponent> otherComponents = getSelection().stream().filter(it -> it != currentNlComponent)
      .map(it -> scene.getSceneComponent(it))
      .filter(it -> it != null)
      .collect(Collectors.toList());

    for (SceneComponent c : otherComponents) {
      List<Target> targets = c.getTargets();
      for (Target t : targets) {
        if (t instanceof MultiComponentTarget) {
          t.mouseCancel();
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
    myFilterType = FilterType.NONE;
    if (myRoot == null) {
      return;
    }

    myNewSelectedComponentsOnDown.clear();
    myHitListener.setTargetFilter(it -> {
      if (it instanceof AnchorTarget) {
        return !((AnchorTarget)it).isEdge();
      }
      return true;
    });
    SecondarySelector secondarySelector = getSelector(transform, x, y);
    myHitListener.find(transform, myRoot, x, y);
    myHitTarget = myHitListener.getClosestTarget();
    myHitComponent = myHitListener.getClosestComponent();
    if (myHitTarget != null) {
      myHitTarget.mouseDown(x, y);
      if (myHitTarget instanceof MultiComponentTarget) {
        delegateMouseDownToSelection(x, y, myHitTarget.getComponent());
      }
    }
    else if (myHitComponent != null && !inCurrentSelection(myHitComponent)) {
      myNewSelectedComponentsOnDown.add(myHitComponent);
      select(myNewSelectedComponentsOnDown);
    }
    else if (findSelectionOfCurve(secondarySelector)) {
      return;
    }
    myHitListener.setTargetFilter(null);
  }

  private SecondarySelector getSelector(@NotNull SceneContext transform,
                                        @AndroidDpCoordinate int x,
                                        @AndroidDpCoordinate int y) {
    Object obj = transform.findClickedGraphics(transform.getSwingXDip(x), transform.getSwingYDip(y));
    if (obj != null && obj instanceof SecondarySelector) {
      return  (SecondarySelector)obj;
    }
    return null;
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

      LassoTarget lassoTarget = null;
      if (myHitTarget instanceof LassoTarget) {
        lassoTarget = (LassoTarget)myHitTarget;

        if (lassoTarget.getSelectWhileDragging() && lassoTarget.getHasChanged()) {
          myNewSelectedComponentsOnRelease.clear();
          myNewSelectedComponentsOnRelease.addAll(lassoTarget.getIntersectingComponents());
          select(myNewSelectedComponentsOnRelease);
          lassoTarget.clearHasChanged();
        }
      }

      myHitListener.setTargetFilter(target -> myHitTarget != target);
      myHitListener.find(transform, myRoot, x, y);
      SceneComponent targetComponent = myHitTarget.getComponent();
      if ((lassoTarget == null || lassoTarget.getIntersectingComponents().isEmpty())
          && targetComponent != null && !inCurrentSelection(targetComponent)) {
        myNewSelectedComponentsOnRelease.clear();
        myNewSelectedComponentsOnRelease.add(targetComponent);
        select(myNewSelectedComponentsOnRelease);
      }
      myHitTarget.mouseDrag(x, y, myHitListener.myHitTargets);
      if (myHitTarget instanceof MultiComponentTarget) {
        delegateMouseDragToSelection(x, y, myHitListener.getClosestTarget(), myHitTarget.getComponent());
      }
      myHitListener.setTargetFilter(null);
    }
    mouseHover(transform, x, y);
    checkRequestLayoutStatus();
  }

  private static boolean isWithinThreshold(@AndroidDpCoordinate int pos1, @AndroidDpCoordinate int pos2, SceneContext transform) {
    @SwingCoordinate int pos3 = transform.getSwingDimensionDip(pos1);
    @SwingCoordinate int pos4 = transform.getSwingDimensionDip(pos2);
    return Math.abs(pos3 - pos4) < DRAG_THRESHOLD;
  }

  public void checkRequestLayoutStatus() {
    if (mNeedsLayout != NO_LAYOUT) {
      SceneManager manager = myDesignSurface.getSceneManager();
      if (manager == null) {
        return;
      }

      if (myIsLiveRenderingEnabled) {
        manager.requestLayoutAndRender(mNeedsLayout == ANIMATED_LAYOUT);
      }
      else {
        manager.requestLayout(mNeedsLayout == ANIMATED_LAYOUT);
      }
    }
  }

  private boolean findSelectionOfCurve(SecondarySelector ss) {
    // find selection of curve
      if (ss == null) {
        return false;
      }
      NlComponent comp = ss.getComponent();
      SecondarySelector.Constraint sub = ss.getConstraint();
      myDesignSurface.getSelectionModel().setSecondarySelection(comp, sub);
      return true;

  }

  /**
   * handles MouseUP event
   */
  public void mouseRelease(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastMouseX = x;
    myLastMouseY = y;

    SceneComponent closestComponent = myHitListener.getClosestComponent();
    if (myHitTarget != null) {
      myHitListener.find(transform, myRoot, x, y);
      Target closest = myHitListener.getFilteredTarget(myHitTarget);
      myHitTarget.mouseRelease(x, y, closest != null ? Collections.singletonList(closest) : Collections.emptyList());
      myHitTarget.getComponent().setDragging(false);
      if (myHitTarget instanceof MultiComponentTarget) {
        delegateMouseReleaseToSelection(x, y, myHitListener.getClosestTarget(), myHitTarget.getComponent());
      }
    }
    myFilterType = FilterType.NONE;
    myNewSelectedComponentsOnRelease.clear();
    if (myHitComponent != null && closestComponent == myHitComponent) {
      myNewSelectedComponentsOnRelease.add(myHitComponent);
    }
    if (myHitTarget != null) {
      List<SceneComponent> changed = myHitTarget.newSelection();
      if (changed != null) {
        myNewSelectedComponentsOnRelease.clear();
        myNewSelectedComponentsOnRelease.addAll(changed);
      }
    }

    SecondarySelector secondarySelector = getSelector(transform, x, y);

    boolean same = sameSelection();
    if (secondarySelector == null && !same && (myHitTarget == null || myHitTarget.canChangeSelection())) {
      select(myNewSelectedComponentsOnRelease);
    }
    else {
      // TODO: Clear in findSelectionOfCurve.
      myDesignSurface.getSelectionModel().clearSecondary();
      findSelectionOfCurve(secondarySelector);
    }
    myHitTarget = null;
    checkRequestLayoutStatus();
  }

  public void mouseCancel() {
    if (myHitTarget != null) {
      myHitTarget.mouseCancel();
      myHitTarget.getComponent().setDragging(false);
      if (myHitTarget instanceof MultiComponentTarget) {
        delegateMouseCancelToSelection(myHitTarget.getComponent());
      }
    }

    myFilterType = FilterType.NONE;
    myNewSelectedComponentsOnRelease.clear();
    myHitTarget = null;
    checkRequestLayoutStatus();
  }

  private boolean inCurrentSelection(@NotNull SceneComponent component) {
    List<NlComponent> currentSelection = myDesignSurface.getSelectionModel().getSelection();
    return currentSelection.contains(component.getNlComponent());
  }

  private boolean sameSelection() {
    if (!myNewSelectedComponentsOnRelease.isEmpty()
        && myNewSelectedComponentsOnRelease.size() == myNewSelectedComponentsOnDown.size()
        && myNewSelectedComponentsOnRelease.containsAll(myNewSelectedComponentsOnDown)) {
      // we already applied the selection on mouseDown
      return true;
    }
    List<NlComponent> currentSelection = myDesignSurface.getSelectionModel().getSelection();
    if (myNewSelectedComponentsOnRelease.size() == currentSelection.size()) {
      int count = currentSelection.size();
      for (int i = 0; i < count; i++) {
        NlComponent component = currentSelection.get(i);
        SceneComponent sceneComponent = getSceneComponent(component);
        if (!myNewSelectedComponentsOnRelease.contains(sceneComponent)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Set a flag to notify that the Scene needs to recompute the layout on the next rendering cycle.
   *
   * @param type Type of layout to recompute: {@link #NO_LAYOUT}, {@link #IMMEDIATE_LAYOUT}, {@link #ANIMATED_LAYOUT}
   */
  public void needsLayout(@MagicConstant(intValues = {NO_LAYOUT, IMMEDIATE_LAYOUT, ANIMATED_LAYOUT}) int type) {
    mNeedsLayout = type;
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
    if (getRoot() != null) {
      addWithin(within, getRoot(), x, y, width, height);
    }
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

  @Nullable
  public Target findTarget(@NotNull SceneContext transform, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myRoot == null) {
      return null;
    }
    myFindListener.find(transform, myRoot, x, y);
    return myFindListener.getClosestTarget();
  }

  public Collection<SceneComponent> getSceneComponents() {
    return mySceneComponents.values();
  }

  public void setRoot(SceneComponent root) {
    myRoot = root;
  }

  @NotNull
  public FilterType getFilterType() {
    return myFilterType;
  }

  public void setFilterType(@NotNull FilterType filterType) {
    myFilterType = filterType;
  }

  @Nullable
  public Target getInteractingTarget() {
    return myHitTarget;
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
    if (!neleComponent.getBackend().isValid()) {
      return null;
    }
    NlModel model = neleComponent.getModel();
    XmlFile xmlFile = model.getFile();
    Module module = model.getModule();
    RenderService renderService = RenderService.getInstance(module.getProject());
    AndroidFacet facet = model.getFacet();
    RenderLogger logger = renderService.createLogger(facet);
    final RenderTask task = renderService.taskBuilder(facet, model.getConfiguration())
      .withLogger(logger)
      .withPsiFile(xmlFile)
      .buildSynchronously();
    if (task == null) {
      return null;
    }

    XmlTag tag = neleComponent.getTagDeprecated();
    ViewInfo viewInfo = task.measureChild(tag, filter);
    if (viewInfo == null) {
      return null;
    }
    viewInfo = RenderService.getSafeBounds(viewInfo);
    return new Dimension(Coordinates.pxToDp(getDesignSurface(), viewInfo.getRight() - viewInfo.getLeft()),
                         Coordinates.pxToDp(getDesignSurface(), viewInfo.getBottom() - viewInfo.getTop()));
  }

  /**
   * Get the {@link Placeholder}s in the Scene without the ones belong to the {@param requester} and its children.
   *
   * @param requester the component which request {@link Placeholder}s
   * @return list of the {@link Placeholder}s for requester
   */
  public List<Placeholder> getPlaceholders(@Nullable SceneComponent requester) {
    ImmutableList.Builder<Placeholder> builder = new ImmutableList.Builder<>();
    doGetPlaceholders(builder, myRoot, requester);
    return builder.build();
  }

  public boolean isLiveRenderingEnabled() {
    return myIsLiveRenderingEnabled;
  }

  public void setLiveRenderingEnabled(boolean enabled) {
    myIsLiveRenderingEnabled = enabled;
  }

  private static void doGetPlaceholders(@NotNull ImmutableList.Builder<Placeholder> builder,
                                        @NotNull SceneComponent component,
                                        @Nullable SceneComponent requester) {
    if (component == requester) {
      return;
    }
    NlComponent nlComponent = component.getNlComponent();
    ViewHandler handler = NlComponentHelperKt.getViewHandler(nlComponent);
    if (handler != null) {
      builder.addAll(handler.getPlaceholders(component));
    }
    for (SceneComponent child : component.getChildren()) {
      doGetPlaceholders(builder, child, requester);
    }
  }

  public void setHitTarget(@Nullable Target hitTarget) {
    myHitTarget = hitTarget;
  }
}
