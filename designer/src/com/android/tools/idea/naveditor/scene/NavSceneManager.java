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
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.SceneView;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.NAVIGATION;

/**
 * {@link SceneManager} for the navigation editor.
 */
public class NavSceneManager extends SceneManager {
  @NavCoordinate private static final int SCREEN_LONG = 256;

  @NavCoordinate private static final int SUBNAV_WIDTH = 140;
  @NavCoordinate private static final int SUBNAV_HEIGHT = 38;

  @SwingCoordinate private static final int PAN_LIMIT = 150;
  @NavCoordinate private static final int BOUNDING_BOX_PADDING = 100;

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

    NavigationSchema.DestinationType type = getDesignSurface().getSchema().getDestinationType(sceneComponent.getNlComponent().getTagName());
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
    NavigationSchema.DestinationType type = getDesignSurface().getSchema().getDestinationType(component.getTagName());
    if (type != null) {
      switch (type) {
        case NAVIGATION:
          if (component != getRoot()) {
            SceneComponent sceneComponent = getScene().getSceneComponent(component);
            if (sceneComponent == null) {
              sceneComponent = new SceneComponent(getScene(), component);
            }
            return sceneComponent;
          }
          return super.createHierarchy(component);
        case FRAGMENT:
        case ACTIVITY:
          return super.createHierarchy(component);
        default:
          //nothing
      }
    }
    return null;
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
    @NavCoordinate Rectangle boundingBox = new Rectangle(0, 0, -1, -1);

    for (SceneComponent child : root.getChildren()) {
      @NavCoordinate Rectangle childRect = child.fillDrawRect(0, null);
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
