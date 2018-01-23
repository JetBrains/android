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

import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.model.ActionType;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.decorator.NavSceneDecoratorFactory;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NavSceneLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.targets.NavScreenTargetProvider;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.util.PropertiesMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.NAVIGATION;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.OTHER;

/**
 * {@link SceneManager} for the navigation editor.
 */
public class NavSceneManager extends SceneManager {
  @NavCoordinate private static final int SCREEN_LONG = 256;

  @NavCoordinate private static final int SUBNAV_WIDTH = 140;
  @NavCoordinate private static final int SUBNAV_HEIGHT = 38;

  @SwingCoordinate private static final int PAN_LIMIT = 150;
  @NavCoordinate private static final int BOUNDING_BOX_PADDING = 100;

  @NavCoordinate public static final int ACTION_ARROW_PARALLEL = 10;
  @NavCoordinate public static final int ACTION_ARROW_PERPENDICULAR = 12;

  @NavCoordinate private static final int ACTION_HEIGHT = ACTION_ARROW_PERPENDICULAR;
  @NavCoordinate private static final int ACTION_VERTICAL_PADDING = 6;

  @NavCoordinate private static final int GLOBAL_ACTION_LINE_LENGTH = 8;
  @NavCoordinate private static final int GLOBAL_ACTION_WIDTH = ACTION_ARROW_PARALLEL + GLOBAL_ACTION_LINE_LENGTH;
  @NavCoordinate private static final int GLOBAL_ACTION_HORIZONTAL_PADDING = 8;

  @NavCoordinate private static final int EXIT_ACTION_LINE_LENGTH = 14;
  @NavCoordinate private static final int EXIT_ACTION_WIDTH = ACTION_ARROW_PARALLEL + EXIT_ACTION_LINE_LENGTH;
  @NavCoordinate private static final int EXIT_ACTION_HORIZONTAL_PADDING = 2;

  private final NavScreenTargetProvider myScreenTargetProvider;

  // TODO: enable layout algorithm switching
  @SuppressWarnings("CanBeFinal") private NavSceneLayoutAlgorithm myLayoutAlgorithm;

  private SceneDecoratorFactory myDecoratorFactory;

  public NavSceneManager(@NotNull NlModel model, @NotNull NavDesignSurface surface) {
    super(model, surface);
    NavigationSchema schema = surface.getSchema();
    myLayoutAlgorithm = new ManualLayoutAlgorithm(model.getModule());
    myScreenTargetProvider = new NavScreenTargetProvider(myLayoutAlgorithm, schema);

    updateHierarchy(getModel(), null);
    getModel().addListener(new ModelChangeListener());
    getDesignSurface().getSelectionModel().addListener((unused, selection) -> getScene().needsRebuildList());
    requestRender();
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
    surface.setShowIssuePanel(false);
    return navView;
  }

  @Override
  protected void updateFromComponent(@NotNull SceneComponent sceneComponent) {
    super.updateFromComponent(sceneComponent);

    NlComponent nlComponent = sceneComponent.getNlComponent();

    switch (NavComponentHelperKt.getActionType(nlComponent)) {
      case GLOBAL:
        sceneComponent.setSize(GLOBAL_ACTION_WIDTH, ACTION_HEIGHT, false);
        return;
      case EXIT:
        sceneComponent.setSize(EXIT_ACTION_WIDTH, ACTION_HEIGHT, false);
        return;
      default:
        break;
    }

    NavigationSchema.DestinationType type = getDesignSurface().getSchema().getDestinationType(nlComponent.getTagName());
    if (type != null) {
      sceneComponent.setTargetProvider(myScreenTargetProvider);

      switch (type) {
        case NAVIGATION:
          if (sceneComponent.getNlComponent() == getDesignSurface().getCurrentNavigation()) {
            // done in post
            sceneComponent.setSize(-1, -1, false);
          }
          else {
            sceneComponent.setSize(SUBNAV_WIDTH, SUBNAV_HEIGHT, false);
          }
          break;
        case FRAGMENT:
        case ACTIVITY:
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
          sceneComponent.setSize(x, y, true);
          break;
        default:
          // nothing
      }
    }
  }

  @Override
  protected void postUpdateFromComponent(@NotNull SceneComponent sceneComponent) {
    NavigationSchema.DestinationType type = getDesignSurface().getSchema().getDestinationType(sceneComponent.getNlComponent().getTagName());
    if (type == NAVIGATION && sceneComponent.getNlComponent() == getDesignSurface().getCurrentNavigation()) {
      layoutAll(sceneComponent);
      updateRootBounds(sceneComponent);
    }
  }

  private void updateRootBounds(@NotNull SceneComponent root) {
    NavDesignSurface surface = getDesignSurface();
    @SwingCoordinate Dimension extentSize = surface.getExtentSize();

    @NavCoordinate int extentWidth = Coordinates.getAndroidDimension(surface, extentSize.width);
    @NavCoordinate int extentHeight = Coordinates.getAndroidDimension(surface, extentSize.height);
    @NavCoordinate int panLimit = Coordinates.getAndroidDimension(surface, PAN_LIMIT);

    @NavCoordinate Rectangle rootBounds = getBoundingBox(root);
    rootBounds.grow(extentWidth - panLimit, extentHeight - panLimit);

    @NavCoordinate int drawX = root.getDrawX();
    @NavCoordinate int drawY = root.getDrawY();

    root.setPosition(rootBounds.x, rootBounds.y);
    root.setSize(rootBounds.width, rootBounds.height, false);

    SceneView view = surface.getCurrentSceneView();
    if (view != null) {
      @SwingCoordinate int deltaX = Coordinates.getSwingDimension(view, root.getDrawX() - drawX);
      @SwingCoordinate int deltaY = Coordinates.getSwingDimension(view, root.getDrawY() - drawY);

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
  @Nullable
  protected SceneComponent createHierarchy(@NotNull NlComponent component) {
    boolean shouldCreateHierarchy = false;

    if (NavComponentHelperKt.isAction(component)) {
      shouldCreateHierarchy = shouldCreateActionHierarchy(component);
    }
    else {
      NavigationSchema.DestinationType destinationType = NavComponentHelperKt.getDestinationType(component);

      if (destinationType != null && destinationType != OTHER) {
        // TODO: handle the OTHER case
        shouldCreateHierarchy = shouldCreateDestinationHierarchy(component);
      }
    }

    if (!shouldCreateHierarchy) {
      return null;
    }

    SceneComponent hierarchy = super.createHierarchy(component);

    if (hierarchy != null && component == getRoot()) {
      moveGlobalActions(hierarchy);
    }

    return hierarchy;
  }


  private boolean shouldCreateDestinationHierarchy(@NotNull NlComponent component) {
    // For destinations, the root navigation and its immediate children should have scene components
    return component == getRoot() || component.getParent() == getRoot();
  }

  private boolean shouldCreateActionHierarchy(@NotNull NlComponent component) {
    ActionType actionType = NavComponentHelperKt.getActionType(component);

    switch (actionType) {
      case GLOBAL:
        // Create scene components for global actions under the root navigation
        return component.getParent() == getRoot();
      case EXIT:
        // Create scene components for exit actions under children of the root navigation
        NlComponent parent = component.getParent();
        return parent != null && parent.getParent() == getRoot();
      default:
        // Regular and self actions are handled as targets
        return false;
    }
  }

  /**
   * Global actions are children of the root navigation in the NlComponent tree, but we want their scene components to be children of
   * the scene component of their destination. This method reparents the scene components of the global actions.
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

    for (SceneComponent component : root.getChildren()) {
      NlComponent child = component.getNlComponent();
      if (NavComponentHelperKt.isAction(child)) {
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

  @Override
  @NotNull
  public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component) {
    return new TemporarySceneComponent(getScene(), component);
  }

  @Override
  public void requestRender() {
    update();
    SceneComponent root = getScene().getRoot();
    if (root != null) {
      root.updateTargets();
      layoutAll(root);
    }
  }

  private void layoutAll(@NotNull SceneComponent root) {
    root.flatten().filter(component -> component.getParent() != null).forEach(component -> component.setPosition(0, 0));
    root.flatten().filter(component -> component.getParent() != null).forEach(myLayoutAlgorithm::layout);

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
        switch (NavComponentHelperKt.getActionType(child.getNlComponent())) {
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
    layoutActions(destination, globalActions, skip, destination.getDrawX() - GLOBAL_ACTION_WIDTH - GLOBAL_ACTION_HORIZONTAL_PADDING);
  }

  private static void layoutExitActions(@NotNull SceneComponent source, @NotNull ArrayList<SceneComponent> exitActions, Boolean skip) {
    layoutActions(source, exitActions, skip, source.getDrawX() + source.getDrawWidth() + EXIT_ACTION_HORIZONTAL_PADDING);
  }

  private static void layoutActions(SceneComponent component, ArrayList<SceneComponent> actions, Boolean skip, @NavCoordinate int x) {
    int count = actions.size();

    if (count == 0) {
      return;
    }

    if (skip) {
      // Insert a null element to indicate that we need space for regular actions
      actions.add((count + 1) / 2, null);
      count++;
    }

    @NavCoordinate int y = component.getDrawY() + component.getDrawHeight() / 2
                           - ACTION_HEIGHT / 2 - (count / 2) * (ACTION_HEIGHT + ACTION_VERTICAL_PADDING);

    for (SceneComponent action : actions) {
      if (action != null) {
        action.setPosition(x, y);
      }
      y += ACTION_HEIGHT + ACTION_VERTICAL_PADDING;
    }
  }

  @Override
  public void layout(boolean animate) {
    SceneComponent root = getScene().getRoot();
    if (root != null) {
      updateRootBounds(root);
    }
    getDesignSurface().updateScrolledAreaSize();
    getScene().needsRebuildList();
  }

  @NotNull
  @Override
  public SceneDecoratorFactory getSceneDecoratorFactory() {
    if (myDecoratorFactory == null) {
      myDecoratorFactory = new NavSceneDecoratorFactory(getDesignSurface().getSchema());
    }
    return myDecoratorFactory;
  }

  @Override
  public Map<Object, PropertiesMap> getDefaultProperties() {
    return ImmutableMap.of();
  }

  // TODO: this should be moved somewhere model-specific, since it is relevant even absent a Scene
  public static void updateHierarchy(@NotNull NlModel model, @Nullable NlModel newModel) {
    List<NlModel.TagSnapshotTreeNode> roots = ImmutableList.of();
    XmlTag newRoot = AndroidPsiUtils.getRootTagSafely(model.getFile());
    if (newModel != null) {
      newRoot = AndroidPsiUtils.getRootTagSafely(newModel.getFile());
      roots = buildTree(newModel.getComponents().stream().map(NlComponent::getTag).toArray(XmlTag[]::new));
    }
    if (newRoot != null) {
      // TODO error handling (if newRoot is null)
      model.syncWithPsi(newRoot, roots);
    }
  }

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {

    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      updateHierarchy(model, model);
      requestRender();
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

    for (SceneComponent child : components) {
      child.fillDrawRect(0, childRect);
      if (boundingBox.width < 0) {
        boundingBox.setBounds(childRect);
      }
      else {
        boundingBox.add(childRect);
      }
    }

    boundingBox.grow(BOUNDING_BOX_PADDING, BOUNDING_BOX_PADDING);

    return boundingBox;
  }
}
