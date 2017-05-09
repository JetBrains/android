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
import com.android.tools.idea.naveditor.scene.decorator.NavSceneDecoratorFactory;
import com.android.tools.idea.naveditor.scene.layout.DummyAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NavSceneLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.targets.NavScreenTargetProvider;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneManager;
import com.android.tools.idea.uibuilder.scene.TemporarySceneComponent;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.android.util.PropertiesMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link SceneManager} for the navigation editor.
 */
public class NavSceneManager extends SceneManager {
  public final NavScreenTargetProvider myScreenTargetProvider;
  private NavSceneLayoutAlgorithm myLayoutAlgorithm;

  private SceneDecoratorFactory myDecoratorFactory;
  private static final String ENABLE_NAV_PROPERTY = "enable.nav.editor";
  private static final int SCREEN_WIDTH = 100;

  public NavSceneManager(@NotNull NlModel model, @NotNull NavDesignSurface surface) {
    super(model, surface);
    NavigationSchema schema = surface.getSchema();
    myLayoutAlgorithm = ManualLayoutAlgorithm.getInstance(model.getFacet());
    surface.zoomActual();
    myScreenTargetProvider = new NavScreenTargetProvider(myLayoutAlgorithm, schema);
  }

  public static boolean enableNavigationEditor() {
    return Boolean.getBoolean(ENABLE_NAV_PROPERTY);
  }

  @Override
  public void addTargets(@NotNull SceneComponent component) {
    // TODO: Remove this method?
  }

  @NotNull
  @Override
  public Scene build() {
    getModel().syncWithPsi(ImmutableList.of());
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
      switch (type) {
        case NAVIGATION:
          // TODO: support nested navigation elements (subflows)
          SceneView view = getDesignSurface().getCurrentSceneView();
          if (view != null) {
            Dimension viewDimension = view.getPreferredSize();
            sceneComponent.setSize(Coordinates.getAndroidDimensionDip(getDesignSurface(), viewDimension.width),
                                   Coordinates.getAndroidDimensionDip(getDesignSurface(), viewDimension.height), true);
          }
          break;
        case FRAGMENT:
        case ACTIVITY:
          Screen screen = getModel().getConfiguration().getDeviceState().getHardware().getScreen();
          // TODO: is this conversion correct?
          double scale = (double)Coordinates.getAndroidDimension(getDesignSurface(), SCREEN_WIDTH)/screen.getXDimension();
          sceneComponent.setSize((int)(scale * screen.getXDimension()), (int)(scale * screen.getYDimension()), true);
          sceneComponent.setTargetProvider(myScreenTargetProvider, false);
          break;
        default:
          // nothing
      }
    }
  }

  public void getContentSize(@NotNull Dimension toFill) {
    SceneComponent root = getScene().getRoot();
    if (root == null) {
      return;
    }
    toFill.setSize(root.getDrawWidth(), root.getDrawWidth());
  }

  @Override
  @Nullable
  protected SceneComponent createHierarchy(@NotNull NlComponent component) {
    NavigationSchema.DestinationType type = getDesignSurface().getSchema().getDestinationType(component.getTagName());
    if (type != null) {
      switch (type) {
        case NAVIGATION:
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
    List<NlComponent> components = getModel().getComponents();
    if (components.size() != 0) {
      NlComponent rootComponent = components.get(0).getRoot();
      SceneComponent root = createHierarchy(rootComponent);
      updateFromComponent(root, new HashSet<>());

      getScene().setRoot(root);
      layoutAll(root);
    }
    getScene().needsRebuildList();
  }

  private void layoutAll(@NotNull SceneComponent root) {
    root.flatten().forEach(myLayoutAlgorithm::layout);
  }

  @Override
  public void layout(boolean animate) {
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

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {

    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
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
      requestRender();
    }

    @Override
    public void modelDeactivated(@NotNull NlModel model) {

    }
  }
}
