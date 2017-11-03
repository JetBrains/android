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
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.java.model.ArtifactModel;
import com.android.java.model.JavaProject;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

/**
 * Action that executed inside Gradle to obtain the project structure (IDEA project and modules) and the custom models for each module (e.g.
 * {@link AndroidProject}.
 */
// (This class replaces org.jetbrains.plugins.gradle.model.ProjectImportAction.)
public class SyncAction implements BuildAction<SyncAction.ProjectModels>, Serializable {
  @NotNull private final Set<Class<?>> myAndroidModelTypes;
  @NotNull private final Set<Class<?>> myJavaModelTypes;

  public SyncAction() {
    this(Collections.emptySet(), Collections.emptySet());
  }

  public SyncAction(@NotNull Set<Class<?>> androidModelTypes, @NotNull Set<Class<?>> javaModelTypes) {
    myAndroidModelTypes = androidModelTypes;
    myJavaModelTypes = javaModelTypes;
  }

  @Override
  @Nullable
  public ProjectModels execute(@NotNull BuildController controller) {
    GradleBuild gradleBuild = controller.getBuildModel();
    ProjectModels models = new ProjectModels(myAndroidModelTypes, myJavaModelTypes);
    models.populate(gradleBuild, controller);
    return models;
  }

  public static class ProjectModels implements Serializable {
    @NotNull private final Set<Class<?>> myAndroidModelTypes;
    @NotNull private final Set<Class<?>> myJavaModelTypes;

    // Key: module's Gradle path.
    @NotNull private final Map<String, ModuleModels> myModelsByModule = new HashMap<>();
    @Nullable private GlobalLibraryMap myGlobalLibraryMap;

    public ProjectModels(@NotNull Set<Class<?>> androidModelTypes, @NotNull Set<Class<?>> javaModelTypes) {
      myAndroidModelTypes = androidModelTypes;
      myJavaModelTypes = javaModelTypes;
    }

    public void populate(@NotNull GradleBuild gradleBuild, @NotNull BuildController controller) {
      BasicGradleProject rootProject = gradleBuild.getRootProject();

      GradleProject root = controller.findModel(rootProject, GradleProject.class);
      populateModels(root, controller);

      // Request for GlobalLibraryMap, it can only be requested by android module.
      // For plugins prior to 3.0.0, controller.findModel returns null.
      for (ModuleModels moduleModels : myModelsByModule.values()) {
        AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
        if (androidProject != null) {
          myGlobalLibraryMap = controller.findModel(moduleModels.findModel(GradleProject.class), GlobalLibraryMap.class);
          break;
        }
      }
    }

    private void populateModels(@NotNull GradleProject project, @NotNull BuildController controller) {
      ModuleModels models = new ModuleModels(project, myAndroidModelTypes, myJavaModelTypes);
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

    /**
     * @return {@link GlobalLibraryMap} retrieved from android plugin.
     * <br/>
     * The return value could be null in two cases:
     * <ol>
     * <li>The version of Android plugin doesn't support GlobalLibraryMap. i.e. pre 3.0.0 plugin.</li>
     * <li>There is no Android module in this project.</li>
     * </ol>
     */
    @Nullable
    public GlobalLibraryMap getGlobalLibraryMap() {
      return myGlobalLibraryMap;
    }
  }

  public static class ModuleModels implements Serializable {
    @NotNull private final GradleProject myGradleProject;
    @NotNull private final Set<Class<?>> myAndroidModelTypes;
    @NotNull private final Set<Class<?>> myJavaModelTypes;

    @NotNull private final Map<Class, Object> myModelsByType = new HashMap<>();

    public ModuleModels(@NotNull GradleProject gradleProject,
                        @NotNull Set<Class<?>> androidModelTypes,
                        @NotNull Set<Class<?>> javaModelTypes) {
      myGradleProject = gradleProject;
      myAndroidModelTypes = androidModelTypes;
      myJavaModelTypes = javaModelTypes;
    }

    public void populate(@NotNull GradleProject gradleProject, @NotNull BuildController controller) {
      myModelsByType.put(GradleProject.class, gradleProject);
      AndroidProject androidProject = findAndAddModel(gradleProject, controller, AndroidProject.class);
      if (androidProject != null) {
        for (Class<?> type : myAndroidModelTypes) {
          findAndAddModel(gradleProject, controller, type);
        }
        // No need to query extra models.
        return;
      }
      NativeAndroidProject ndkAndroidProject = findAndAddModel(gradleProject, controller, NativeAndroidProject.class);
      if (ndkAndroidProject != null) {
        // No need to query extra models.
        return;
      }
      JavaProject javaProject = findAndAddModel(gradleProject, controller, JavaProject.class);
      if (javaProject != null) {
        for (Class<?> type : myJavaModelTypes) {
          findAndAddModel(gradleProject, controller, type);
        }
        return;
      }
      // Jar/Aar module.
      findAndAddModel(gradleProject, controller, ArtifactModel.class);
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
