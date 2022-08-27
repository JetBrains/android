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
package com.android.tools.idea.gradle.run;

import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link BuildAction} to be run when building project pre run. It returns a class containing all the needed
 * post build models. e.g. {@link ProjectBuildOutput} and {@link InstantAppProjectBuildOutput}.
 *
 * <p> These models are used for obtaining information not known in sync time. e.g. built apks when using
 * config splits and package name for instant apps.
 */
public class OutputBuildAction implements BuildAction<OutputBuildAction.PostBuildProjectModels>, Serializable {
  @NotNull private final ImmutableCollection<String> myGradlePaths;

  OutputBuildAction(@NotNull Collection<String> moduleGradlePaths) {
    myGradlePaths = ImmutableSet.copyOf(moduleGradlePaths);
  }

  @TestOnly
  @NotNull
  Collection<String> getMyGradlePaths() {
    return myGradlePaths;
  }

  @Override
  public OutputBuildAction.PostBuildProjectModels execute(@NotNull BuildController controller) {
    PostBuildProjectModels postBuildProjectModels = new PostBuildProjectModels();

    if (!myGradlePaths.isEmpty()) {
      BasicGradleProject rootProject = controller.getBuildModel().getRootProject();
      GradleProject root = controller.findModel(rootProject, GradleProject.class);

      postBuildProjectModels.populate(root, myGradlePaths, controller);
    }

    return postBuildProjectModels;
  }

  public static class PostBuildProjectModels implements Serializable {
    // Key: module's Gradle path.
    @NotNull private final Map<String, PostBuildModuleModels> myModelsByModule = new HashMap<>();

    private PostBuildProjectModels() {}

    public void populate(@NotNull GradleProject rootProject,
                         @NotNull Collection<String> gradleModulePaths,
                         @NotNull BuildController controller) {
      for (String gradleModulePath : gradleModulePaths) {
        populateModule(rootProject, gradleModulePath, controller);
      }
    }

    private void populateModule(@NotNull GradleProject rootProject,
                                @NotNull String moduleProjectPath,
                                @NotNull BuildController controller) {
      if (myModelsByModule.containsKey(moduleProjectPath)) {
        // Module models already found
        return;
      }

      GradleProject moduleProject = rootProject.findByPath(moduleProjectPath);
      if (moduleProject != null) {
        PostBuildModuleModels models = new PostBuildModuleModels(moduleProject);
        models.populate(controller);
        myModelsByModule.put(moduleProject.getPath(), models);
      }
    }

    @Nullable
    public PostBuildModuleModels getModels(@NotNull String gradlePath) {
      return myModelsByModule.get(gradlePath);
    }
  }

  public static class PostBuildModuleModels implements Serializable {
    @NotNull private final GradleProject myGradleProject;
    @NotNull private final Map<Class, Object> myModelsByType = new HashMap<>();

    private PostBuildModuleModels(@NotNull GradleProject gradleProject) {
      myGradleProject = gradleProject;
    }

    private void populate(@NotNull BuildController controller) {
      findAndAddModel(controller, AppBundleProjectBuildOutput.class);
      ProjectBuildOutput projectBuildOutput = findAndAddModel(controller, ProjectBuildOutput.class);
      if (projectBuildOutput != null) {
        return;
      }

      findAndAddModel(controller, InstantAppProjectBuildOutput.class);
    }

    @NotNull
    public String getGradlePath() {
      return myGradleProject.getPath();
    }

    @NotNull
    public String getModuleName() {
      return myGradleProject.getName();
    }

    @Nullable
    private <T> T findAndAddModel(@NotNull BuildController controller, @NotNull Class<T> modelType) {
      T model = controller.findModel(myGradleProject, modelType);
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