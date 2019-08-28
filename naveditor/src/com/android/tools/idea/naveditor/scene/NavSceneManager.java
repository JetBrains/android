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
package com.android.tools.idea.naveditor.scene;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.HitProvider;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.model.ActionType;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.decorator.NavSceneDecoratorFactory;
import com.android.tools.idea.naveditor.scene.layout.ElkLayeredLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NavSceneLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NewDestinationLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.targets.NavActionTargetProvider;
import com.android.tools.idea.naveditor.scene.targets.NavScreenTargetProvider;
import com.android.tools.idea.naveditor.scene.targets.NavigationTargetProvider;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link SceneManager} for the navigation editor.
 */
public class NavSceneManager extends SceneManager {
  @NavCoordinate private static final int SCREEN_LONG = JBUI.scale(256);

  @NavCoordinate public static final int SUBNAV_WIDTH = JBUI.scale(140);
  @NavCoordinate public static final int SUBNAV_HEIGHT = JBUI.scale(38);

  @SwingCoordinate private static final int PAN_LIMIT = JBUI.scale(150);
  @NavCoordinate private static final int BOUNDING_BOX_PADDING = JBUI.scale(100);

  @NavCoordinate public static final float ACTION_ARROW_PARALLEL = JBUI.scale(10f);
  @NavCoordinate public static final float ACTION_ARROW_PERPENDICULAR = JBUI.scale(12f);

  @NavCoordinate private static final float ACTION_HEIGHT = ACTION_ARROW_PERPENDICULAR;
  @NavCoordinate private static final int ACTION_VERTICAL_PADDING = JBUI.scale(6);
  @NavCoordinate private static final int POP_ICON_VERTICAL_PADDING = JBUI.scale(10);

  @NavCoordinate private static final int ACTION_LINE_LENGTH = JBUI.scale(14);
  @NavCoordinate private static final float ACTION_WIDTH = ACTION_ARROW_PARALLEL + ACTION_LINE_LENGTH;
  @NavCoordinate private static final int ACTION_HORIZONTAL_PADDING = JBUI.scale(8);

  private final NavScreenTargetProvider myScreenTargetProvider;
  private final NavigationTargetProvider myNavigationTargetProvider;
  private final NavActionTargetProvider myNavActionTargetProvider;
  private final HitProvider myNavActionSourceHitProvider = new NavActionSourceHitProvider();
  private final HitProvider myNavDestinationHitProvider = new NavDestinationHitProvider();
  private final HitProvider myHorizontalActionHitProvider = new NavHorizontalActionHitProvider();

  private final List<NavSceneLayoutAlgorithm> myLayoutAlgorithms;
  private final NavSceneLayoutAlgorithm mySavingLayoutAlgorithm;

  private SceneDecoratorFactory myDecoratorFactory;

  public NavSceneManager(@NotNull NlModel model, @NotNull NavDesignSurface surface, @NotNull RenderSettings settings) {
    super(model, surface, () -> settings);
    createSceneView();
    myLayoutAlgorithms = ImmutableList.of(
      new NewDestinationLayoutAlgorithm(),
      new ManualLayoutAlgorithm(model.getModule(), this),
      new ElkLayeredLayoutAlgorithm());
    mySavingLayoutAlgorithm = myLayoutAlgorithms.stream().filter(algorithm -> algorithm.canSave()).findFirst().orElse(null);
    myScreenTargetProvider = new NavScreenTargetProvider();
    myNavigationTargetProvider = new NavigationTargetProvider(surface);
    myNavActionTargetProvider = new NavActionTargetProvider();

    updateHierarchy(getModel(), null);
    getModel().addListener(new ModelChangeListener());
    getDesignSurface().getSelectionModel().addListener((unused, selection) -> getScene().needsRebuildList());
  }

  public NavSceneManager(@NotNull NlModel model, @NotNull NavDesignSurface surface) {
    this(model, surface, RenderSettings.getProjectSettings(model.getProject()));
  }

    @Override
  @NotNull
  protected NavDesignSurface getDesignSurface() {
    return (NavDesignSurface)super.getDesignSurface();
  }

  @Override
  @NotNull
  protected SceneView doCreateSceneView() {
    NavDesignSurface surface = getDesignSurface();
    NavView navView = new NavView(surface, this);
    surface.getLayeredPane().setPreferredSize(navView.getPreferredSize());
    return navView;
  }

  @Override
  protected void updateFromComponent(@NotNull SceneComponent sceneComponent) {
    super.updateFromComponent(sceneComponent);

    NlComponent nlComponent = sceneComponent.getNlComponent();

    if (isHorizontalAction(nlComponent)) {
      sceneComponent.setSize((int)ACTION_WIDTH, (int)ACTION_HEIGHT);
      return;
    }

    NavigationSchema.DestinationType type = NavComponentHelperKt.getDestinationType(nlComponent);
    if (type != null) {
      sceneComponent.setTargetProvider(sceneComponent.getNlComponent() == getDesignSurface().getCurrentNavigation()
                                       ? myNavigationTargetProvider
                                       : myScreenTargetProvider);
      sceneComponent.updateTargets();

      switch (type) {
        case NAVIGATION:
          if (sceneComponent.getNlComponent() == getDesignSurface().getCurrentNavigation()) {
            // done in post
            sceneComponent.setSize(-1, -1);
          }
          else {
            sceneComponent.setSize(SUBNAV_WIDTH, SUBNAV_HEIGHT);
          }
          break;
        case FRAGMENT:
        case ACTIVITY:
        case OTHER:
          State state = getModel().getConfiguration().getDeviceState();
          assert state != null;
          Screen screen = state.getHardware().getScreen();
          @NavCoordinate int x = SCREEN_LONG;
          @NavCoordinate int y = SCREEN_LONG;
          double ratio = screen.getXDimension() / (double)screen.getYDimension();
          if (ratio > 1) {
            y /= ratio;
          }
          else {
            x *= ratio;
          }
          if (ratio < 1.1 && ratio > 0.9) {
            // If it's approximately square make it smaller, otherwise it takes up too much space.
            x *= 0.5;
            y *= 0.5;
          }
          if ((state.getOrientation() == ScreenOrientation.LANDSCAPE) == (ratio < 1)) {
            int tmp = x;
            //noinspection SuspiciousNameCombination
            x = y;
            y = tmp;
          }
          sceneComponent.setSize(x, y);
          break;
        default:
          // nothing
      }
    }
    else if (NavComponentHelperKt.isAction(sceneComponent.getNlComponent())) {
      sceneComponent.setTargetProvider(myNavActionTargetProvider);
      sceneComponent.updateTargets();
    }
  }

  @Override
  public void update() {
    Rectangle rootBounds = null;
    if (getScene().getRoot() != null) {
      rootBounds = getScene().getRoot().fillDrawRect(0, null);
    }

    super.update();

    SceneComponent root = getScene().getRoot();
    if (root != null) {
      root.updateTargets();
      layoutAll(root);
    }

    updateRootBounds(rootBounds);
  }

  private void updateRootBounds(@Nullable @NavCoordinate Rectangle prevRootBounds) {
    SceneComponent root = getScene().getRoot();
    if (root == null) {
      return;
    }

    NavDesignSurface surface = getDesignSurface();

    @SwingCoordinate Dimension extentSize = surface.getExtentSize();
    @NavCoordinate int extentWidth = Coordinates.getAndroidDimension(surface, extentSize.width);
    @NavCoordinate int extentHeight = Coordinates.getAndroidDimension(surface, extentSize.height);

    @NavCoordinate Rectangle rootBounds;

    if (isEmpty()) {
      rootBounds = new Rectangle(0, 0, extentWidth, extentHeight);
    }
    else {
      @NavCoordinate int panLimit = Coordinates.getAndroidDimension(surface, PAN_LIMIT);
      rootBounds = getBoundingBox(root);
      rootBounds.grow(extentWidth - panLimit, extentHeight - panLimit);
    }

    root.setPosition(rootBounds.x, rootBounds.y);
    root.setSize(rootBounds.width, rootBounds.height);
    surface.updateScrolledAreaSize();

    SceneView view = surface.getFocusedSceneView();
    if (view != null) {
      @SwingCoordinate int deltaX = Coordinates.getSwingDimension(view, root.getDrawX() - (prevRootBounds == null ? 0 : prevRootBounds.x));
      @SwingCoordinate int deltaY = Coordinates.getSwingDimension(view, root.getDrawY() - (prevRootBounds == null ? 0 : prevRootBounds.y));

      @SwingCoordinate Point point = surface.getScrollPosition();
      surface.setScrollPosition(point.x - deltaX, point.y - deltaY);
    }
  }

  @Override
  @NotNull
  protected NlComponent getRoot() {
    return getDesignSurface().getCurrentNavigation();
  }

  @Override
  @NotNull
  protected List<SceneComponent> createHierarchy(@NotNull NlComponent component) {
    boolean shouldCreateHierarchy = false;

    if (NavComponentHelperKt.isAction(component)) {
      shouldCreateHierarchy = true;
    }
    else if (NavComponentHelperKt.isDestination(component)) {
      shouldCreateHierarchy = shouldCreateDestinationHierarchy(component);
    }

    if (!shouldCreateHierarchy) {
      return ImmutableList.of();
    }

    List<SceneComponent> hierarchy = super.createHierarchy(component);

    if (component == getRoot()) {
      for (SceneComponent child : hierarchy) {
        moveGlobalActions(child);
        moveRegularActions(child);
      }
    }
    else if (NavComponentHelperKt.isNavigation(component)) {
      List<SceneComponent> exits = findAndCreateExitActionComponents(component);
      if (!exits.isEmpty()) {
        return ImmutableList.<SceneComponent>builder().addAll(hierarchy).addAll(exits).build();
      }
    }

    return hierarchy;
  }

  private List<SceneComponent> findAndCreateExitActionComponents(NlComponent component) {
    return component.flatten()
                    .filter(c -> {
                      if (!NavComponentHelperKt.isAction(c)) {
                        return false;
                      }
                      NlComponent destination = NavComponentHelperKt.getActionDestination(c);
                      return destination != null && destination.getParent() == getRoot();
                    })
                    .map(c -> {
                      SceneComponent sceneComponent = getScene().getSceneComponent(c);
                      if (sceneComponent == null) {
                        sceneComponent = new SceneComponent(getScene(), c, getHitProvider(c));
                      }
                      return sceneComponent;
                    })
                    .collect(Collectors.toList());
  }

  private boolean shouldCreateDestinationHierarchy(@NotNull NlComponent component) {
    // For destinations, the root navigation and its immediate children should have scene components
    return component == getRoot() || component.getParent() == getRoot();
  }

  /**
   * Global actions are children of the root navigation in the NlComponent tree, but we want their scene components to be children of
   * the scene component of their destination. This method re-parents the scene components of the global actions.
   * <p>
   * TODO: in SceneManager.createHierarchy we try to reuse SceneComponents if possible. Moving SceneComponents in this way prevents that
   * from working.
   */
  private void moveGlobalActions(@NotNull SceneComponent root) {
    Map<String, SceneComponent> destinationMap = new HashMap<>();

    for (SceneComponent component : root.getChildren()) {
      NlComponent child = component.getNlComponent();
      if (NavComponentHelperKt.isDestination(child)) {
        destinationMap.put(child.getId(), component);
      }
    }

    ArrayList<SceneComponent> globalActions = new ArrayList<>();

    NlComponent rootNlComponent = root.getNlComponent();
    for (SceneComponent component : root.getChildren()) {
      NlComponent child = component.getNlComponent();
      // Make sure we're actually an nl child: exit actions are children of the root scenecomponent and will already be in this list.
      if (NavComponentHelperKt.isAction(child) && child.getParent() == rootNlComponent) {
        globalActions.add(component);
      }
    }

    for (SceneComponent globalAction : globalActions) {
      String destination = NavComponentHelperKt.getActionDestinationId(globalAction.getNlComponent());
      SceneComponent parent = destinationMap.get(destination);
      if (parent == null) {
        getScene().removeComponent(globalAction);
      }
      else {
        parent.addChild(globalAction);
      }
    }
  }

  /**
   * Regular actions are children of a destination in the NlComponent tree, but we want their scene components to be children of
   * the root. This method re-parents the scene components of the regular actions.
   * <p>
   * TODO: decide if this is also what we should do for other action types, and if so restore clips for components (remove custom
   * NavScreenDecorator#buildListChildren).
   */
  private static void moveRegularActions(@NotNull SceneComponent root) {
    for (SceneComponent destinationSceneComponent : root.getChildren()) {
      NlComponent destinationNlComponent = destinationSceneComponent.getNlComponent();
      if (NavComponentHelperKt.isDestination(destinationNlComponent)) {
        for (SceneComponent actionSceneComponent : destinationSceneComponent.getChildren()) {
          NlComponent actionNlComponent = actionSceneComponent.getNlComponent();
          if (NavComponentHelperKt.getActionType(actionNlComponent, root.getNlComponent()) == ActionType.REGULAR) {
            actionSceneComponent.removeFromParent();
            root.addChild(actionSceneComponent);
          }
        }
      }
    }
  }

  @Override
  @NotNull
  public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component) {
    return new TemporarySceneComponent(getScene(), component);
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestRender() {
    boolean wasEmpty = getScene().getRoot() == null || getScene().getRoot().getChildCount() == 0;
    update();
    if (wasEmpty) {
      getDesignSurface().zoomToFit();
    }

    return CompletableFuture.completedFuture(null);
  }

  private void layoutAll(@NotNull SceneComponent root) {
    List<SceneComponent> destinations = new ArrayList<>();

    for (SceneComponent child : root.getChildren()) {
      if (NavComponentHelperKt.isDestination(child.getNlComponent())) {
        destinations.add(child);
      }
    }

    for (NavSceneLayoutAlgorithm algorithm : myLayoutAlgorithms) {
      List<SceneComponent> remaining = algorithm.layout(destinations);
      destinations.removeAll(remaining);
      // If the algorithm that laid out the component can't persist the position, assume the position hasn't been persisted and
      // needs to be
      if (!algorithm.canSave()) {
        save(destinations);
      }
      if (remaining.isEmpty()) {
        break;
      }
      destinations = remaining;
    }

    HashSet<String> connectedActionSources = new HashSet<>();
    HashSet<String> connectedActionDestinations = new HashSet<>();

    getConnectedActions(root.getNlComponent(), connectedActionSources, connectedActionDestinations);

    for (SceneComponent component : root.getChildren()) {
      NlComponent nlComponent = component.getNlComponent();

      if (!NavComponentHelperKt.isDestination(nlComponent)) {
        continue;
      }

      ArrayList<SceneComponent> globalActions = new ArrayList<>();
      ArrayList<SceneComponent> exitActions = new ArrayList<>();

      for (SceneComponent child : component.getChildren()) {
        switch (NavComponentHelperKt.getActionType(child.getNlComponent(), getRoot())) {
          case GLOBAL:
            globalActions.add(child);
            break;
          case EXIT:
            exitActions.add(child);
            break;
          default:
            break;
        }
      }

      String id = nlComponent.getId();

      layoutGlobalActions(component, globalActions, connectedActionDestinations.contains(id));
      layoutExitActions(component, exitActions, connectedActionSources.contains(id));
    }
  }

  public void save(@NotNull List<SceneComponent> components) {
    if (mySavingLayoutAlgorithm != null) {
      components.forEach(mySavingLayoutAlgorithm::save);
    }
  }

  @Nullable
  public Object getPositionData(@NotNull SceneComponent component) {
    if (mySavingLayoutAlgorithm != null) {
      return mySavingLayoutAlgorithm.getPositionData(component);
    }
    return null;
  }

  public void restorePositionData(@NotNull List<String> path, @NotNull Object positionData) {
    if (mySavingLayoutAlgorithm != null) {
      mySavingLayoutAlgorithm.restorePositionData(path, positionData);
    }
  }

  /**
   * Builds up a list of ids of sources and destinations for all actions
   * whose source and destination are currently visible
   * These are used to layout the global and exit actions properly
   */
  private static void getConnectedActions(@NotNull NlComponent root,
                                          @NotNull HashSet<String> connectedActionSources,
                                          @NotNull HashSet<String> connectedActionDestinations) {
    HashSet<String> children = new HashSet<>();
    for (NlComponent child : root.getChildren()) {
      children.add(child.getId());
    }

    for (NlComponent component : root.getChildren()) {
      if (!NavComponentHelperKt.isDestination(component)) {
        continue;
      }

      // TODO: Handle duplicate ids
      component.flatten()
               .filter(NavComponentHelperKt::isAction)
               .forEach(action -> {
                 String destinationId = NavComponentHelperKt.getEffectiveDestinationId(action);
                 if (children.contains(destinationId)) {
                   connectedActionSources.add(component.getId());
                   if (!NavComponentHelperKt.isSelfAction(action)) {
                     connectedActionDestinations.add(destinationId);
                   }
                 }
               });
    }
  }

  private static void layoutGlobalActions(@NotNull SceneComponent destination,
                                          @NotNull ArrayList<SceneComponent> globalActions,
                                          Boolean skip) {
    layoutActions(destination, globalActions, skip, (int)(destination.getDrawX() - ACTION_WIDTH - ACTION_HORIZONTAL_PADDING));
  }

  private static void layoutExitActions(@NotNull SceneComponent source, @NotNull ArrayList<SceneComponent> exitActions, Boolean skip) {
    layoutActions(source, exitActions, skip, source.getDrawX() + source.getDrawWidth() + ACTION_HORIZONTAL_PADDING);
  }

  private static void layoutActions(SceneComponent component, ArrayList<SceneComponent> actions, Boolean skip, @NavCoordinate int x) {
    int count = actions.size();

    if (count == 0) {
      return;
    }

    int popIconCount = 0;

    for (int i = 0; i < (count + 1) / 2; i++) {
      if (NavComponentHelperKt.getPopUpTo(actions.get(i).getNlComponent()) != null) {
        popIconCount++;
      }
    }

    if (skip) {
      // Insert a null element to indicate that we need space for regular actions
      actions.add((count + 1) / 2, null);
      count++;
    }

    @NavCoordinate int y = component.getDrawY() + component.getDrawHeight() / 2
                           - (int)ACTION_HEIGHT / 2 - (count / 2) * (int)(ACTION_HEIGHT + ACTION_VERTICAL_PADDING)
                           - (popIconCount * POP_ICON_VERTICAL_PADDING);

    for (SceneComponent action : actions) {
      if (action != null) {
        if (NavComponentHelperKt.getPopUpTo(action.getNlComponent()) != null) {
          y += POP_ICON_VERTICAL_PADDING;
        }

        action.setPosition(x, y);
      }
      y += ACTION_HEIGHT + ACTION_VERTICAL_PADDING;
    }
  }

  private boolean isHorizontalAction(@NotNull NlComponent component) {
    ActionType actionType = (NavComponentHelperKt.getActionType(component, getRoot()));
    return actionType == ActionType.GLOBAL || actionType == ActionType.EXIT;
  }

  @NotNull
  @Override
  public CompletableFuture<Void> requestLayout(boolean animate) {
    Rectangle bounds = null;
    if (getScene().getRoot() != null) {
      bounds = getScene().getRoot().fillDrawRect(0, null);
    }
    updateRootBounds(bounds);
    getDesignSurface().updateScrolledAreaSize();
    getScene().needsRebuildList();

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void layout(boolean animate) {
    requestLayout(animate);
  }

  @NotNull
  @Override
  public SceneDecoratorFactory getSceneDecoratorFactory() {
    if (myDecoratorFactory == null) {
      myDecoratorFactory = new NavSceneDecoratorFactory();
    }
    return myDecoratorFactory;
  }

  @Override
  public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultProperties() {
    return ImmutableMap.of();
  }

  @Override
  public Map<Object, String> getDefaultStyles() {
    return ImmutableMap.of();
  }

  // TODO: this should be moved somewhere model-specific, since it is relevant even absent a Scene
  public static void updateHierarchy(@NotNull NlModel model, @Nullable NlModel newModel) {
    List<NlModel.TagSnapshotTreeNode> roots = ImmutableList.of();
    XmlTag newRoot = AndroidPsiUtils.getRootTagSafely(model.getFile());
    if (newModel != null) {
      newRoot = AndroidPsiUtils.getRootTagSafely(newModel.getFile());
      roots = buildTree(newModel.getComponents().stream().map(NlComponent::getTagDeprecated).toArray(XmlTag[]::new));
    }
    if (newRoot != null) {
      // TODO error handling (if newRoot is null)
      model.syncWithPsi(newRoot, roots);
    }
  }

  public boolean isEmpty() {
    return getDesignSurface().getCurrentNavigation().getChildren().stream().noneMatch(c -> NavComponentHelperKt.isDestination(c));
  }

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {

    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      updateHierarchy(model, model);
      getDesignSurface().refreshRoot();
      requestRender();
      model.notifyListenersModelUpdateComplete();
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      boolean previous = getScene().isAnimated();
      UIUtil.invokeLaterIfNeeded(() -> {
        getScene().setAnimated(animate);
        update();
        getScene().setAnimated(previous);
      });
    }

    @Override
    public void modelActivated(@NotNull NlModel model) {
      updateHierarchy(model, model);
      requestRender();
    }

    @Override
    public void modelDeactivated(@NotNull NlModel model) {

    }
  }

  private static List<NlModel.TagSnapshotTreeNode> buildTree(XmlTag[] roots) {
    List<NlModel.TagSnapshotTreeNode> result = new ArrayList<>();
    for (XmlTag root : roots) {
      NlModel.TagSnapshotTreeNode node = new NlModel.TagSnapshotTreeNode() {
        @Override
        public TagSnapshot getTagSnapshot() {
          return TagSnapshot.createTagSnapshot(root, null);
        }

        @NotNull
        @Override
        public List<NlModel.TagSnapshotTreeNode> getChildren() {
          return buildTree(root.getSubTags());
        }
      };
      result.add(node);
    }
    return result;
  }

  @NavCoordinate
  @NotNull
  public static Rectangle getBoundingBox(@NotNull SceneComponent root) {
    return getBoundingBox(root.getChildren());
  }

  @NavCoordinate
  @NotNull
  public static Rectangle getBoundingBox(@NotNull List<SceneComponent> components) {
    @NavCoordinate Rectangle boundingBox = new Rectangle(0, 0, -1, -1);
    @NavCoordinate Rectangle childRect = new Rectangle();

    components.stream().filter(c -> NavComponentHelperKt.isDestination(c.getNlComponent())).forEach(child -> {
      child.fillDrawRect(0, childRect);
      if (boundingBox.width < 0) {
        boundingBox.setBounds(childRect);
      }
      else {
        boundingBox.add(childRect);
      }
    });

    boundingBox.grow(BOUNDING_BOX_PADDING, BOUNDING_BOX_PADDING);

    return boundingBox;
  }

  @NotNull
  @Override
  public HitProvider getHitProvider(@NotNull NlComponent component) {
    if (NavComponentHelperKt.getSupportsActions(component)) {
      return myNavActionSourceHitProvider;
    }
    else if (isHorizontalAction(component)) {
      return myHorizontalActionHitProvider;
    }

    return myNavDestinationHitProvider;
  }

  public void performUndoablePositionAction(@NotNull NlComponent component) {
    SceneComponent sceneComponent = getScene().getSceneComponent(component);
    if (sceneComponent == null) {
      return;
    }
    Object positionData = getPositionData(sceneComponent);
    List<String> path = NavComponentHelperKt.getIdPath(component);

    UndoManager.getInstance(getDesignSurface().getProject()).undoableActionPerformed(
      new BasicUndoableAction(getModel().getFile().getVirtualFile()) {
      @Override
      public void undo() {
        if (positionData == null) {
          return;
        }
        restorePositionData(path, positionData);
      }

      @Override
      public void redo() {
      }
    });
  }
}
