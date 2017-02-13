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
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Action that executed inside Gradle to obtain the project structure (IDEA project and modules) and the custom models for each module (e.g.
 * {@link AndroidProject}.
 */
// (This class replaces org.jetbrains.plugins.gradle.model.ProjectImportAction.)
public class SyncAction implements BuildAction<SyncAction.ProjectModels>, Serializable {
  @Override
  @Nullable
  public ProjectModels execute(@NotNull BuildController controller) {
    GradleBuild gradleBuild = controller.getBuildModel();
    ProjectModels models = new ProjectModels();
    models.populate(gradleBuild, controller);
    return models;
  }

  public static class ProjectModels implements Serializable {
    // Key: module's Gradle path.
    @NotNull private final Map<String, ModuleModels> myModelsByModule = new HashMap<>();
    //@NotNull private final Map<String, ModuleModels2> myModelsByModule2 = new HashMap<>();

    public void populate(@NotNull GradleBuild gradleBuild, @NotNull BuildController controller) {
      BasicGradleProject rootProject = gradleBuild.getRootProject();

      GradleProject root = controller.findModel(rootProject, GradleProject.class);
      populateModels(root, controller);
    }

    private void populateModels(@NotNull GradleProject project, @NotNull BuildController controller) {
      ModuleModels models = new ModuleModels(project.getPath());
      models.populate(project, controller);
      myModelsByModule.put(project.getPath(), models);

      for (GradleProject child : project.getChildren()) {
        populateModels(child, controller);
      }
    }

    @NotNull
    public Collection<String> getProjectPaths() {
      return myModelsByModule.keySet();
    }

    @Nullable
    public ModuleModels getModels(@NotNull String gradlePath) {
      return myModelsByModule.get(gradlePath);
    }
  }

  public static class ModuleModels implements Serializable {
    @NotNull private final Map<Class<?>, Object> myModelsByType = new HashMap<>();
    @NotNull private final String myGradlePath;

    public ModuleModels(@NotNull String gradlePath) {
      myGradlePath = gradlePath;
    }

    public void populate(@NotNull GradleProject gradleProject, @NotNull BuildController controller) {
      myModelsByType.put(GradleProject.class, gradleProject);
      AndroidProject androidProject = findAndAddModel(gradleProject, controller, AndroidProject.class);
      if (androidProject != null) {
        // No need to query extra models.
        return;
      }
      NativeAndroidProject ndkAndroidProject = findAndAddModel(gradleProject, controller, NativeAndroidProject.class);
      if (ndkAndroidProject != null) {
        // No need to query extra models.
        return;
      }
      // This is a Java module.
      // TODO use the new Java library model here.
    }

    @NotNull
    public String getGradlePath() {
      return myGradlePath;
    }

    @Nullable
    private <T> T findAndAddModel(@NotNull GradleProject gradleProject, @NotNull BuildController controller, Class<T> modelType) {
      T model = controller.findModel(gradleProject, modelType);
      if (model != null) {
        myModelsByType.put(modelType, model);
      }
      return model;
    }

    public <T> boolean hasModel(@NotNull Class<T> modelType) {
      return findModel(modelType) != null;
    }

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
}
