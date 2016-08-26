/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.google.common.collect.Maps;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * Action that executed inside Gradle to obtain the project structure (IDEA project and modules) and the custom models for each module (e.g.
 * {@link com.android.builder.model.AndroidProject}.
 */
// (This class replaces org.jetbrains.plugins.gradle.model.ProjectImportAction.)
public class SyncAction implements BuildAction<SyncAction.ProjectModels>, Serializable {
  // These are the types of models to obtain from Gradle (e.g. AndroidProject.)
  @NotNull private final Collection<Class<?>> myModelTypes = new HashSet<>();

  public SyncAction(@NotNull Collection<Class<?>> modelTypes) {
    myModelTypes.addAll(modelTypes);
  }

  @Override
  @Nullable
  public ProjectModels execute(@NotNull BuildController controller) {
    IdeaProject project = controller.getModel(IdeaProject.class);
    if (project == null) {
      return null;
    }
    ProjectModels models = new ProjectModels(project);
    models.populate(controller, myModelTypes);
    return models;
  }

  public static class ProjectModels implements Serializable {
    @NotNull private final IdeaProject myProject;

    // Key: module's Gradle path.
    @NotNull private final Map<String, ModuleModels> myModelsByModule = Maps.newHashMap();

    public ProjectModels(@NotNull IdeaProject project) {
      myProject = project;
    }

    public void populate(@NotNull BuildController controller, @NotNull Collection<Class<?>> modelTypes) {
      for (IdeaModule module : myProject.getModules()) {
        String key = createMapKey(module);
        ModuleModels models = new ModuleModels(module);
        models.populate(controller, modelTypes);
        myModelsByModule.put(key, models);
      }
    }

    @NotNull
    public ModuleModels getModels(@NotNull IdeaModule module) {
      String key = createMapKey(module);
      return getModels(key);
    }

    @NotNull
    private static String createMapKey(@NotNull IdeaModule module) {
      return module.getGradleProject().getPath();
    }

    @NotNull
    public ModuleModels getModels(@NotNull String gradlePath) {
      return myModelsByModule.get(gradlePath);
    }

    @NotNull
    public IdeaProject getProject() {
      return myProject;
    }
  }

  public static class ModuleModels implements Serializable {
    @NotNull private final IdeaModule myModule;

    @NotNull private final Map<Class<?>, Object> myModelsByType = Maps.newHashMap();

    public ModuleModels(@NotNull IdeaModule module) {
      myModule = module;
    }

    public void populate(@NotNull BuildController controller, @NotNull Collection<Class<?>> modelTypes) {
      for (Class<?> modelType : modelTypes) {
        Object model = controller.findModel(myModule, modelType);
        if (model != null) {
          myModelsByType.put(modelType, model);
        }
      }
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

    @NotNull
    public IdeaModule getModule() {
      return myModule;
    }
  }
}
