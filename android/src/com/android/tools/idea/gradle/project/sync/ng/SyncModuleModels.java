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
import com.android.builder.model.NativeAndroidProject;
import com.android.java.model.ArtifactModel;
import com.android.java.model.JavaProject;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SyncModuleModels implements GradleModuleModels {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  @NotNull private final BuildIdentifier myBuildId;
  @NotNull private final Set<Class<?>> myExtraAndroidModelTypes;
  @NotNull private final Set<Class<?>> myExtraJavaModelTypes;

  @NotNull private final Map<Class, Object> myModelsByType = new HashMap<>();

  @NotNull private String myModuleName;

  SyncModuleModels(@NotNull GradleProject gradleProject,
                   @NotNull BuildIdentifier buildId,
                   @NotNull Set<Class<?>> extraAndroidModelTypes,
                   @NotNull Set<Class<?>> extraJavaModelTypes) {
    myBuildId = buildId;
    myExtraAndroidModelTypes = extraAndroidModelTypes;
    myExtraJavaModelTypes = extraJavaModelTypes;
    myModuleName = gradleProject.getName();
  }

  void populate(@NotNull GradleProject gradleProject, @NotNull BuildController controller) {
    myModelsByType.put(GradleProject.class, gradleProject);
    AndroidProject androidProject = findAndAddModel(gradleProject, controller, AndroidProject.class);
    if (androidProject != null) {
      // "Native" projects also both AndroidProject and AndroidNativeProject
      findAndAddModel(gradleProject, controller, NativeAndroidProject.class);
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

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * Include project name in module name to make sure module names are unique. For example, MyApplication-app.
   * Call this method if current module has duplicated name with another module.
   */
  public void deduplicateModuleName() {
    myModuleName = myBuildId.getRootDir().getName() + "-" + myModuleName;
  }

  /**
   * @return the build identifier of current module.
   */
  @NotNull
  public BuildIdentifier getBuildId() {
    return myBuildId;
  }

  @Nullable
  private <T> T findAndAddModel(@NotNull GradleProject gradleProject, @NotNull BuildController controller, @NotNull Class<T> modelType) {
    T model = controller.findModel(gradleProject, modelType);
    if (model != null) {
      myModelsByType.put(modelType, model);
    }
    return model;
  }

  @Override
  @Nullable
  public <T> T findModel(@NotNull Class<T> modelType) {
    Object model = myModelsByType.get(modelType);
    if (model != null) {
      assert modelType.isInstance(model);
      return modelType.cast(model);
    }
    return null;
  }
}
