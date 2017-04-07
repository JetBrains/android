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

import com.android.tools.idea.naveditor.scene.decorator.NavSceneDecoratorFactory;
import com.android.tools.idea.naveditor.scene.layout.DummyAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.layout.NavSceneLayoutAlgorithm;
import com.android.tools.idea.naveditor.scene.targets.NavScreenTargetProvider;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneManager;
import com.android.tools.idea.uibuilder.scene.TemporarySceneComponent;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.google.common.collect.ImmutableList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SceneManager} for the navigation editor.
 */
public class NavSceneManager extends SceneManager {
  public static final String TAG_FRAGMENT = "fragment";
  public static final String ATTR_DESTINATION = "destination";
  public static final String TAG_ACTION = "action";
  public static final String TAG_NAVIGATION = "navigation";
  public final NavScreenTargetProvider myScreenTargetProvider;
  private NavSceneLayoutAlgorithm myLayoutAlgorithm;

  private static final SceneDecoratorFactory DECORATOR_FACTORY = new NavSceneDecoratorFactory();
  private static final String ENABLE_NAV_PROPERTY = "enable.nav.editor";

  public NavSceneManager(@NotNull NlModel model, @NotNull DesignSurface surface) {
    super(model, surface);
    myLayoutAlgorithm = new ManualLayoutAlgorithm(new DummyAlgorithm());
    surface.zoomActual();
    myScreenTargetProvider = new NavScreenTargetProvider(myLayoutAlgorithm);
  }

  public static boolean enableNavigationEditor() {
    return Boolean.getBoolean(ENABLE_NAV_PROPERTY);
  }

  @Override
  protected void addTargets(@NotNull SceneComponent component) {
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
  protected void updateFromComponent(@NotNull NlComponent component, @NotNull SceneComponent sceneComponent) {
    if (component.getTagName().equals(TAG_NAVIGATION)) {
      SceneView view = getDesignSurface().getCurrentSceneView();
      if (view != null) {
        Dimension viewDimension = view.getPreferredSize();
        sceneComponent.setSize(Coordinates.getAndroidDimensionDip(getDesignSurface(), viewDimension.width),
                               Coordinates.getAndroidDimensionDip(getDesignSurface(), viewDimension.height), true);
      }
    }
    else if (component.getTagName().equals(TAG_FRAGMENT)) {
      sceneComponent.setSize(50, 50, true);
      sceneComponent.setTargetProvider(myScreenTargetProvider, false);
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
  protected SceneComponent updateFromComponent(@NotNull NlComponent component, @NotNull Set<SceneComponent> seenComponents) {
    switch (component.getTagName()) {
      case "navigation":
      case TAG_FRAGMENT:
        return super.updateFromComponent(component, seenComponents);
      default:
        //nothing
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
      SceneComponent root = updateFromComponent(rootComponent, new HashSet<>());

      getScene().setRoot(root);
      @SwingCoordinate Dimension surfaceSize = getDesignSurface().getSize();
      root.setSize(Coordinates.getAndroidDimensionDip(getDesignSurface(), (int)surfaceSize.getWidth()),
                   Coordinates.getAndroidDimensionDip(getDesignSurface(), (int)surfaceSize.getHeight()),
                   false);
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
    return DECORATOR_FACTORY;
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
