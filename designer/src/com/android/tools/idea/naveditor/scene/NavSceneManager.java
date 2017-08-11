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
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.naveditor.scene.decorator.NavSceneDecoratorFactory;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NavSceneLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.targets.NavScreenTargetProvider;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.SceneView;
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
  private static final int SUBNAV_WIDTH = 100;
  private static final int SUBNAV_HEIGHT = 25;
  private final NavScreenTargetProvider myScreenTargetProvider;
  
  // TODO: enable layout algorithm switching
  @SuppressWarnings("CanBeFinal") private NavSceneLayoutAlgorithm myLayoutAlgorithm;

  private SceneDecoratorFactory myDecoratorFactory;
  private static final String ENABLE_NAV_PROPERTY = "enable.nav.editor";

  public NavSceneManager(@NotNull NlModel model, @NotNull NavDesignSurface surface) {
    super(model, surface);
    NavigationSchema schema = surface.getSchema();
    myLayoutAlgorithm = new ManualLayoutAlgorithm(model.getModule());
    surface.zoomActual();
    myScreenTargetProvider = new NavScreenTargetProvider(myLayoutAlgorithm, schema);
  }

  public static boolean enableNavigationEditor() {
    return Boolean.getBoolean(ENABLE_NAV_PROPERTY);
  }

  @NotNull
  @Override
  public Scene build() {
    updateHierarchy(getModel(), null);
    Scene scene = super.build();
    getModel().addListener(new ModelChangeListener());
    getDesignSurface().getSelectionModel().addListener((model, selection) -> scene.needsRebuildList());
    requestRender();
    return scene;
  }

  @Override
  @NotNull
  public NavDesignSurface getDesignSurface() {
    return (NavDesignSurface)super.getDesignSurface();
  }

  @Override
  protected void updateFromComponent(@NotNull SceneComponent sceneComponent) {
    super.updateFromComponent(sceneComponent);
    NavigationSchema.DestinationType type = getDesignSurface().getSchema().getDestinationType(sceneComponent.getNlComponent().getTagName());
    if (type != null) {
      sceneComponent.setTargetProvider(myScreenTargetProvider, false);
      switch (type) {
        case NAVIGATION:
          if (sceneComponent.getNlComponent() == getDesignSurface().getCurrentNavigation()) {
            // done in post
            sceneComponent.setSize(-1, -1, false);
          }
          else {
            // TODO: take label size into account.
            sceneComponent.setSize(SUBNAV_WIDTH, SUBNAV_HEIGHT, false);
          }
          break;
        case FRAGMENT:
        case ACTIVITY:
          State state = getModel().getConfiguration().getDeviceState();
          assert state != null;
          Screen screen = state.getHardware().getScreen();
          sceneComponent.setSize(screen.getXDimension() / 4, screen.getYDimension() / 4, true);
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
    Rectangle bounds = new Rectangle(0, 0, -1, -1);
    Rectangle temp = new Rectangle();
    Rectangle rootBounds = root.fillRect(null);
    // TODO: include targets
    root.flatten().filter(c -> c != root).forEach(component -> {
      if (component.isDragging()) {
        // If we're dragging, don't shrink
        if (bounds.width < 0) {
          bounds.setBounds(rootBounds);
        }
        else {
          bounds.add(rootBounds);
        }
        // Add the center of the component, so you have to actually drag it properly outside the current bounds to have an effect.
        bounds.add(component.getCenterX(), component.getCenterY());
      }
      else {
        Rectangle componentBounds = component.fillDrawRect(0, temp);
        componentBounds.setLocation(componentBounds.x - 50, componentBounds.y - 50);
        componentBounds.setSize(componentBounds.width + 100, componentBounds.height + 100);
        if (bounds.width < 0) {
          bounds.setBounds(componentBounds);
        }
        else {
          bounds.add(componentBounds);
        }
      }
    });
    NavDesignSurface surface = getDesignSurface();
    SceneView view = surface.getCurrentSceneView();
    if (view != null) {
      // If the origin of the root has shifted, offset the view so the screen doesn't jump around
      surface.getCurrentSceneView().setLocation(Math.max(0, Coordinates.getSwingXDip(view, bounds.x)),
                                                Math.max(0, Coordinates.getSwingYDip(view, bounds.y)));
    }
    root.setPosition(bounds.x, bounds.y);
    root.setSize(bounds.width, bounds.height, false);
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
      root.updateTargets(true);
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
    public void modelRendered(@NotNull NlModel model) {
      // updateFrom needs to be called in the dispatch thread
      UIUtil.invokeLaterIfNeeded(NavSceneManager.this::update);
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

}
