/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidProject;
import com.google.common.collect.Maps;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;

/**
 * Imports an Android project using a single call to the Gradle Tooling API.
 */
public class ProjectImportAction implements BuildAction<ProjectImportAction.AllModels>, Serializable {
  @NonNls private static final String ANDROID_TASK_NAME_PREFIX = "android";

  @Nullable
  @Override
  public AllModels execute(BuildController controller) {
    IdeaProject ideaProject = controller.getModel(IdeaProject.class);
    if (ideaProject == null || ideaProject.getModules().isEmpty()) {
      return null;
    }

    AllModels allModels = new AllModels(ideaProject);

    for (IdeaModule module : ideaProject.getModules()) {
      if (isAndroidProject(module)) {
        AndroidProject androidProject = controller.getModel(module, AndroidProject.class);
        allModels.addAndroidProject(androidProject, module);
      }
    }

    return allModels.hasAndroidProjects() ? allModels : null;
  }

  private static boolean isAndroidProject(@NotNull IdeaModule module) {
    // A Gradle project is an Android project is if has at least one task with name starting with 'android'.
    for (GradleTask task : module.getGradleProject().getTasks()) {
      String taskName = task.getName();
      if (taskName != null && taskName.startsWith(ANDROID_TASK_NAME_PREFIX)) {
        return true;
      }
    }
    return false;
  }

  public static class AllModels implements Serializable {
    @NotNull private final Map<String, AndroidProject> androidProjectsByPath = Maps.newHashMap();
    @NotNull private final IdeaProject myIdeaProject;

    public AllModels(@NotNull IdeaProject project) {
      myIdeaProject = project;
    }

    @NotNull
    public IdeaProject getIdeaProject() {
      return myIdeaProject;
    }

    public void addAndroidProject(@NotNull AndroidProject project, @NotNull IdeaModule module) {
      androidProjectsByPath.put(extractMapKey(module), project);
    }

    @Nullable
    public AndroidProject getAndroidProject(@NotNull IdeaModule module) {
      return androidProjectsByPath.get(extractMapKey(module));
    }

    @NotNull
    private static String extractMapKey(@NotNull IdeaModule module) {
      return module.getGradleProject().getPath();
    }

    public boolean hasAndroidProjects() {
      return !androidProjectsByPath.isEmpty();
    }
  }
}
