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
package com.android.tools.idea.uibuilder.scene;

import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.common.surface.ShapePolicyKt.SQUARE_SHAPE_POLICY;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.LayoutScannerConfiguration;
import com.android.tools.idea.common.surface.LayoutScannerEnabled;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SceneManager} that creates a Scene from an NlModel representing a layout using layoutlib.
 */
public class LayoutlibSceneManager extends NewLayoutlibSceneManager {
  @NotNull private final ViewEditor myViewEditor;

  /**
   * Creates a new LayoutlibSceneManager.
   *
   * @param model                      the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface              the {@link DesignSurface} user to present the result of the renders.
   * @param renderTaskDisposerExecutor {@link Executor} to be used for running the slow {@link #dispose()} calls.
   * @param sceneComponentProvider     a {@link SceneComponentHierarchyProvider} providing the mapping from
   *                                   {@link NlComponent} to {@link SceneComponent}s.
   * @param layoutScannerConfig        a {@link LayoutScannerConfiguration} for layout validation from Accessibility Testing Framework.
   */
  protected LayoutlibSceneManager(@NotNull NlModel model,
                                  @NotNull DesignSurface<? extends LayoutlibSceneManager> designSurface,
                                  @NotNull Executor renderTaskDisposerExecutor,
                                  @NotNull SceneComponentHierarchyProvider sceneComponentProvider,
                                  @NotNull LayoutScannerConfiguration layoutScannerConfig) {
    super(model, designSurface, renderTaskDisposerExecutor, sceneComponentProvider, layoutScannerConfig);
    updateSceneView();

    getDesignSurface().getSelectionModel().addListener(selectionChangeListener);

    Scene scene = getScene();

    myViewEditor = new ViewEditorImpl(model, scene);

    model.getConfiguration().addListener(configurationChangeListener);

    List<NlComponent> components = model.getTreeReader().getComponents();
    if (!components.isEmpty()) {
      NlComponent rootComponent = components.get(0).getRoot();

      boolean previous = getScene().isAnimated();
      scene.setAnimated(false);
      List<SceneComponent> hierarchy = sceneComponentProvider.createHierarchy(this, rootComponent);
      SceneComponent root = hierarchy.isEmpty() ? null : hierarchy.get(0);
      if (root != null) {
        updateFromComponent(root, new HashSet<>());
        scene.setRoot(root);
        updateTargets();
        scene.setAnimated(previous);
      }
      else {
        Logger.getInstance(LayoutlibSceneManager.class).warn("No root component");
      }
    }

    model.addListener(modelChangeListener);
    areListenersRegistered = true;

    // let's make sure the selection is correct
    scene.selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());
  }

  /**
   * Creates a new LayoutlibSceneManager with the default settings for running render requests, but with accessibility testing
   * framework scanner disabled.
   * See {@link LayoutlibSceneManager#LayoutlibSceneManager(NlModel, DesignSurface, Executor, SceneComponentHierarchyProvider, LayoutScannerConfiguration)}
   *
   * @param model                  the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface          the {@link DesignSurface} user to present the result of the renders.
   * @param sceneComponentProvider a {@link SceneComponentHierarchyProvider providing the mapping from {@link NlComponent} to
   *                               {@link SceneComponent}s.
   */
  public LayoutlibSceneManager(@NotNull NlModel model,
                               @NotNull DesignSurface<LayoutlibSceneManager> designSurface,
                               @NotNull SceneComponentHierarchyProvider sceneComponentProvider) {
    this(
      model,
      designSurface,
      AppExecutorUtil.getAppExecutorService(),
      sceneComponentProvider,
      new LayoutScannerEnabled());
    getSceneRenderConfiguration().getLayoutScannerConfig().setLayoutScannerEnabled(false);
  }

  /**
   * Creates a new LayoutlibSceneManager with the default settings for running render requests.
   *
   * @param model the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface the {@link DesignSurface} user to present the result of the renders.
   * @param config configuration for layout validation when rendering.
   */
  public LayoutlibSceneManager(@NotNull NlModel model, @NotNull DesignSurface<LayoutlibSceneManager> designSurface, LayoutScannerConfiguration config) {
    this(
      model,
      designSurface,
      AppExecutorUtil.getAppExecutorService(),
      new LayoutlibSceneManagerHierarchyProvider(),
      config);
  }

  @NotNull
  public ViewEditor getViewEditor() {
    return myViewEditor;
  }

  @NotNull
  @Override
  protected SceneView doCreateSceneView() {
    NlModel model = getModel();

    DesignerEditorFileType type = model.getType();

    if (type == MenuFileType.INSTANCE) {
      return createSceneViewsForMenu();
    }

    SceneView primarySceneView = getDesignSurface().getScreenViewProvider().createPrimarySceneView(getDesignSurface(), this);
    setSecondarySceneView(getDesignSurface().getScreenViewProvider().createSecondarySceneView(getDesignSurface(), this));

    getDesignSurface().updateErrorDisplay();

    return primarySceneView;
  }

  private SceneView createSceneViewsForMenu() {
    NlModel model = getModel();
    XmlTag tag = model.getFile().getRootTag();
    SceneView sceneView;

    // TODO See if there's a better way to trigger the NavigationViewSceneView. Perhaps examine the view objects?
    if (tag != null && Objects.equals(tag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI), NavigationViewSceneView.SHOW_IN_ATTRIBUTE_VALUE)) {
      sceneView = ScreenView.newBuilder(getDesignSurface(), this)
        .withLayersProvider((sv) -> {
          ColorBlindMode colorBlindMode = getDesignSurface().getScreenViewProvider().getColorBlindFilter();
          return ImmutableList.of(new ScreenViewLayer(sv, colorBlindMode, getDesignSurface(), getDesignSurface()::getRotateSurfaceDegree));
        })
        .withContentSizePolicy(NavigationViewSceneView.CONTENT_SIZE_POLICY)
        .withShapePolicy(SQUARE_SHAPE_POLICY)
        .build();
    }
    else {
      sceneView = ScreenView.newBuilder(getDesignSurface(), this).build();
    }

    getDesignSurface().updateErrorDisplay();
    return sceneView;
  }
}
