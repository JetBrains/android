/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.tools.lint.checks.AnnotationDetectorKt.RESTRICT_TO_ANNOTATION;
import static com.android.tools.idea.rendering.StudioRenderServiceKt.taskBuilder;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.UtilsKt;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.StudioRenderService;
import com.android.tools.idea.rendering.parsers.PsiXmlTag;
import com.android.tools.idea.rendering.parsers.RenderXmlTag;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.awt.Dimension;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of the {@link ViewEditor} abstraction presented
 * to {@link ViewHandler} instances
 */
public class ViewEditorImpl extends ViewEditor {
  private final Configuration myConfiguration;
  private final NlModel myModel;
  private final SceneManager mySceneManager;
  private final Scene myScene;

  @VisibleForTesting
  private Collection<ViewInfo> myRootViews;

  public ViewEditorImpl(@NotNull SceneView sceneView) {
    this(sceneView.getSceneManager().getModel(), sceneView.getScene());
  }

  public ViewEditorImpl(@NotNull NlModel model, @Nullable Scene scene) {
    myConfiguration = model.getConfiguration();
    myModel = model;
    myScene = scene;
    mySceneManager = scene != null ? scene.getSceneManager() : null;
  }

  @Nullable
  @Override
  public AndroidVersion getCompileSdkVersion() {
    return StudioAndroidModuleInfo.getInstance(myModel.getFacet()).getBuildSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    return StudioAndroidModuleInfo.getInstance(myModel.getFacet()).getMinSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return StudioAndroidModuleInfo.getInstance(myModel.getFacet()).getTargetSdkVersion();
  }

  @NotNull
  @Override
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  @Override
  public NlModel getModel() {
    return myModel;
  }

  @NotNull
  @Override
  public LayoutlibSceneManager getSceneBuilder() {
    assert mySceneManager != null : "ViewEditorImpl incorrectly configured";
    return (LayoutlibSceneManager)mySceneManager;
  }

  @NotNull
  @Override
  public Collection<ViewInfo> getRootViews() {
    if (myRootViews != null) {
      return myRootViews;
    }

    RenderResult result = getSceneBuilder().getRenderResult();

    if (result == null) {
      return Collections.emptyList();
    }

    return result.getRootViews();
  }

  @VisibleForTesting
  public void setRootViews(@NotNull Collection<ViewInfo> rootViews) {
    myRootViews = rootViews;
  }

  @NotNull
  @Override
  public CompletableFuture<Map<NlComponent, Dimension>> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter) {
    // TODO: Reuse snapshot!
    if (!parent.getBackend().isValid()) {
      return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    if (parent.getChildCount() == 0) {
      return CompletableFuture.completedFuture(Collections.emptyMap());
    }
    Map<XmlTag, NlComponent> tagToComponent = Maps.newHashMapWithExpectedSize(parent.getChildCount());
    for (NlComponent child : parent.getChildren()) {
      tagToComponent.put(child.getTagDeprecated(), child);
    }

    NlModel model = myModel;
    XmlFile xmlFile = model.getFile();
    Module module = model.getModule();
    RenderService renderService = StudioRenderService.getInstance(module.getProject());
    final CompletableFuture<RenderTask> taskFuture = taskBuilder(renderService, model.getFacet(), getConfiguration())
      .withPsiFile(xmlFile)
      .build();

    // Measure unweighted bounds
    XmlTag parentTag = parent.getTagDeprecated();
    return taskFuture.thenCompose(task -> {
      if (task == null) {
        return CompletableFuture.completedFuture(Collections.emptyMap());
      }
      return task.measureChildren(new PsiXmlTag(parentTag), filter)
        .whenCompleteAsync((map, ex) -> task.dispose(), AppExecutorUtil.getAppExecutorService())
        .thenApply(map -> {
          if (map == null) {
            return Collections.emptyMap();
          }

          Map<NlComponent, Dimension> unweightedSizes = Maps.newHashMap();
          for (Map.Entry<RenderXmlTag, ViewInfo> entry : map.entrySet()) {
            ViewInfo viewInfo = entry.getValue();
            viewInfo = RenderService.getSafeBounds(viewInfo);
            Dimension size = new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
            NlComponent child = tagToComponent.get(entry.getKey());
            if (child != null) {
              unweightedSizes.put(child, size);
            }
          }

          return unweightedSizes;
        });
    });
  }

  @VisibleForTesting
  static boolean isPublicAndUnRestricted(@NotNull PsiClass psiClass) {
    PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers == null) {
      return false;
    }
    if (!modifiers.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    for (PsiAnnotation annotation : modifiers.getAnnotations()) {
      if (RESTRICT_TO_ANNOTATION.isEquals(annotation.getQualifiedName())) {
        return false;
      }
    }
    return true;
  }

  @Override
  @NotNull
  public Scene getScene() {
    assert myScene != null : "ViewEditorImpl incorrectly configured";
    return myScene;
  }

  @Override
  public boolean canInsertChildren(@NotNull NlComponent parent, @NotNull List<NlComponent> children, int index) {
    return getModel().canAddComponents(children, parent, getChild(parent, index));
  }

  @Override
  public void insertChildren(@NotNull NlComponent parent, @NotNull List<NlComponent> children, int index, @NotNull InsertType insertType) {
    UtilsKt.addComponentsAndSelectedIfCreated(getModel(),
                                              children,
                                              parent,
                                              getChild(parent, index),
                                              insertType,
                                              myScene.getDesignSurface().getSelectionModel());
  }

  @Nullable
  private static NlComponent getChild(@NotNull NlComponent parent, int index) {
    return 0 <= index && index < parent.getChildCount() ? parent.getChild(index) : null;
  }

  @Override
  public boolean moduleDependsOnAppCompat() {
    return NlModelHelperKt.moduleDependsOnAppCompat(myModel);
  }
}