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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.common.model.NlDependencyManager;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import static com.android.tools.lint.checks.AnnotationDetector.RESTRICT_TO_ANNOTATION;

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
  private NlDependencyManager myDependencyManager;

  public ViewEditorImpl(@NotNull SceneView sceneView) {
    this(sceneView.getModel(), sceneView.getScene());
  }

  public ViewEditorImpl(@NotNull NlModel model) {
    this(model, null);
  }

  public ViewEditorImpl(@NotNull NlModel model, @Nullable Scene scene) {
    myConfiguration = model.getConfiguration();
    myModel = model;
    myScene = scene;
    mySceneManager = scene != null ? scene.getSceneManager() : null;
    myDependencyManager = NlDependencyManager.Companion.get();
  }

  @Nullable
  @Override
  public AndroidVersion getCompileSdkVersion() {
    return AndroidModuleInfo.getInstance(myModel.getFacet()).getBuildSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    return AndroidModuleInfo.getInstance(myModel.getFacet()).getMinSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return AndroidModuleInfo.getInstance(myModel.getFacet()).getTargetSdkVersion();
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

  @Nullable
  @Override
  public Map<NlComponent, Dimension> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter) {
    // TODO: Reuse snapshot!
    Map<NlComponent, Dimension> unweightedSizes = Maps.newHashMap();
    XmlTag parentTag = parent.getTag();
    if (parentTag.isValid()) {
      if (parent.getChildCount() == 0) {
        return Collections.emptyMap();
      }
      Map<XmlTag, NlComponent> tagToComponent = Maps.newHashMapWithExpectedSize(parent.getChildCount());
      for (NlComponent child : parent.getChildren()) {
        tagToComponent.put(child.getTag(), child);
      }

      NlModel model = myModel;
      XmlFile xmlFile = model.getFile();
      AndroidFacet facet = model.getFacet();
      RenderService renderService = RenderService.getInstance(facet);
      RenderLogger logger = renderService.createLogger();
      final RenderTask task = renderService.createTask(xmlFile, getConfiguration(), logger, null);
      if (task == null) {
        return null;
      }

      // Measure unweighted bounds
      Map<XmlTag, ViewInfo> map = task.measureChildren(parentTag, filter);
      task.dispose();
      if (map != null) {
        for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
          ViewInfo viewInfo = entry.getValue();
          viewInfo = RenderService.getSafeBounds(viewInfo);
          Dimension size = new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
          NlComponent child = tagToComponent.get(entry.getKey());
          if (child != null) {
            unweightedSizes.put(child, size);
          }
        }
      }
    }

    return unweightedSizes;
  }

  @Nullable
  @Override
  public String displayResourceInput(@NotNull String title, @NotNull EnumSet<ResourceType> types) {
    NlModel model = myModel;
    ChooseResourceDialog dialog = ChooseResourceDialog.builder()
      .setModule(model.getModule())
      .setTypes(types)
      .setConfiguration(model.getConfiguration())
      .build();

    if (!title.isEmpty()) {
      dialog.setTitle(title);
    }

    dialog.show();

    if (dialog.isOK()) {
      String resource = dialog.getResourceName();

      if (resource != null && !resource.isEmpty()) {
        return resource;
      }
    }

    return null;
  }

  @Nullable
  @Override
  public String displayClassInput(@NotNull String title,
                                  @NotNull Set<String> superTypes,
                                  @Nullable Predicate<String> filter,
                                  @Nullable String currentValue) {
    Module module = myModel.getModule();
    String[] superTypesArray = ArrayUtil.toStringArray(superTypes);

    Predicate<PsiClass> psiFilter = ChooseClassDialog.getIsPublicAndUnrestrictedFilter();
    if (filter == null) {
      filter = ChooseClassDialog.getIsUserDefinedFilter();
    }
    psiFilter = psiFilter.and(ChooseClassDialog.qualifiedNameFilter(filter));
    return ChooseClassDialog.openDialog(module, title, currentValue, psiFilter, superTypesArray);
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
    getModel().addComponents(children, parent, getChild(parent, index), insertType, this.myScene.getDesignSurface());
  }

  @NotNull
  @Override
  public NlDependencyManager getDependencyManager() {
    return myDependencyManager;
  }

  @Nullable
  private static NlComponent getChild(@NotNull NlComponent parent, int index) {
    return 0 <= index && index < parent.getChildCount() ? parent.getChild(index) : null;
  }


  /**
   * Try to get an existing View editor from the {@link SceneView}'s {@link SceneManager}
   *
   * @deprecated use {@link #getOrCreate(Scene)} instead.
   */
  @Deprecated
  @NotNull
  public static ViewEditor getOrCreate(@NotNull SceneView sceneView) {
    return getOrCreate(sceneView.getScene());
  }

  /**
   * Try to get an existing View editor from the {@link Scene}'s {@link SceneManager}
   */
  @NotNull
  public static ViewEditor getOrCreate(@NotNull Scene scene) {
    return scene.getSceneManager().getViewEditor();
  }
}