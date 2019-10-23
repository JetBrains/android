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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ModelBuilderParameter;
import com.android.builder.model.NativeAndroidProject;
import com.android.java.model.ArtifactModel;
import com.android.java.model.GradlePluginModel;
import com.android.java.model.JavaProject;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;

public class SyncModuleModels implements GradleModuleModels {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 5L;

  @NotNull private final BuildIdentifier myBuildId;
  @NotNull private final Set<Class<?>> myExtraAndroidModelTypes;
  @NotNull private final Set<Class<?>> myExtraJavaModelTypes;
  @NotNull private final SyncActionOptions myOptions;

  @NotNull private final Map<Class, List<Object>> myModelsByType = new HashMap<>();

  @NotNull private String myModuleName;

  public SyncModuleModels(@NotNull GradleProject gradleProject,
                          @NotNull BuildIdentifier buildId,
                          @NotNull Set<Class<?>> extraAndroidModelTypes,
                          @NotNull Set<Class<?>> extraJavaModelTypes,
                          @NotNull SyncActionOptions options) {
    myBuildId = buildId;
    myExtraAndroidModelTypes = extraAndroidModelTypes;
    myExtraJavaModelTypes = extraJavaModelTypes;
    myModuleName = gradleProject.getName();
    myOptions = options;
  }

  void populate(@NotNull GradleProject gradleProject, @NotNull BuildController controller) {
    addModel(GradleProject.class, gradleProject);
    findAndAddModel(gradleProject, controller, GradlePluginModel.class);
    findAndAddModel(gradleProject, controller, BuildScriptClasspathModel.class);
    AndroidProject androidProject = findParameterizedAndroidModel(gradleProject, controller, AndroidProject.class);
    if (androidProject != null) {
      // "Native" projects also both AndroidProject and AndroidNativeProject
      findParameterizedAndroidModel(gradleProject, controller, NativeAndroidProject.class);
      for (Class<?> type : myExtraAndroidModelTypes) {
        findAndAddModel(gradleProject, controller, type);
      }
      // No need to query extra models.
      return;
    }
    JavaProject javaProject = findAndAddModel(gradleProject, controller, JavaProject.class);
    if (javaProject != null) {
      for (Class<?> type : myExtraJavaModelTypes) {
        findAndAddModel(gradleProject, controller, type);
      }
      return;
    }
    // Jar/Aar module.
    findAndAddModel(gradleProject, controller, ArtifactModel.class);
  }

  @Nullable
  private <T> T findParameterizedAndroidModel(@NotNull GradleProject gradleProject,
                                              @NotNull BuildController controller,
                                              @NotNull Class<T> modelType) {
    if (myOptions.isSingleVariantSyncEnabled()) {
      try {
        T androidModel = controller.getModel(gradleProject, modelType, ModelBuilderParameter.class,
                                             parameter -> parameter.setShouldBuildVariant(false));
        if (androidModel != null) {
          addModel(modelType, androidModel);
          return androidModel;
        }
      }
      catch (UnsupportedVersionException e) {
        // Using old version of Gradle. Fall back to full variants sync for this module.
      }
    }
    return findAndAddModel(gradleProject, controller, modelType);
  }

  @Nullable
  private <T> T findAndAddModel(@NotNull GradleProject gradleProject, @NotNull BuildController controller, @NotNull Class<T> modelType) {
    T model = controller.findModel(gradleProject, modelType);
    if (model != null) {
      addModel(modelType, model);
    }
    return model;
  }

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * Set module name to make sure module names are unique.
   * Call this method if current module has duplicated name with another module.
   */
  public void setModuleName(@NotNull String moduleName) {
    myModuleName = moduleName;
  }

  /**
   * @return the build identifier of current module.
   */
  @NotNull
  public BuildIdentifier getBuildId() {
    return myBuildId;
  }

  @Override
  @Nullable
  public <T> T findModel(@NotNull Class<T> modelType) {
    List<Object> models = myModelsByType.get(modelType);
    if (models == null || models.isEmpty()) {
      return null;
    }
    assert models.size() == 1 : "More than one models available, please use findModels() instead.";
    Object model = models.get(0);
    assert modelType.isInstance(model);
    return modelType.cast(model);
  }

  @Override
  @Nullable
  public <T> List<T> findModels(@NotNull Class<T> modelType) {
    List<Object> models = myModelsByType.get(modelType);
    if (models == null || models.isEmpty()) {
      return null;
    }
    return models.stream().map(model -> {
      assert modelType.isInstance(model);
      return modelType.cast(model);
    }).collect(Collectors.toList());
  }

  public <T> void addModel(@NotNull Class<T> modelType, @NotNull T model) {
    List<Object> models = myModelsByType.computeIfAbsent(modelType, k -> new ArrayList<>());
    models.add(model);
  }
}
