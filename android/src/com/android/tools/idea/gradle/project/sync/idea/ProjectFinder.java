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
package com.android.tools.idea.gradle.project.sync.idea;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

class ProjectFinder {
  private static final Key<CopyOnWriteArrayList<Project>> NEW_PROJECTS_KEY = Key.create("idea.gradle.sync.new.projects");

  // This is needed because AndroidGradleProjectResolver cannot find a reference to a project is the project is new and has not been opened
  // in the IDE yet. This is an issue that only occurs with IDEA's Gradle Sync infrastructure.
  static void registerAsNewProject(@NotNull Project project) {
    CopyOnWriteArrayList<Project> newProjects = getNewProjects();
    if (newProjects == null) {
      newProjects = new CopyOnWriteArrayList<>();
      ApplicationManager.getApplication().putUserData(NEW_PROJECTS_KEY, newProjects);
    }
    newProjects.addIfAbsent(project);
  }

  static void unregisterAsNewProject(@NotNull Project project) {
    Application application = ApplicationManager.getApplication();
    CopyOnWriteArrayList<Project> newProjects = application.getUserData(NEW_PROJECTS_KEY);
    if (newProjects != null) {
      newProjects.remove(project);
    }
  }

  @Nullable
  Project findProject(@NotNull ProjectResolverContext context) {
    String projectPath = context.getProjectPath();
    if (isNotEmpty(projectPath)) {
      File projectFolderPath = toSystemDependentPath(projectPath);
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        if (hasMatchingPath(project, projectFolderPath)) {
          return project;
        }
      }
      CopyOnWriteArrayList<Project> newProjects = getNewProjects();
      if (newProjects != null) {
        for (Project project : newProjects) {
          if (hasMatchingPath(project, projectFolderPath)) {
            return project;
          }
        }
      }
    }
    return null;
  }

  private static boolean hasMatchingPath(@NotNull Project project, @NotNull File path) {
    String basePath = project.getBasePath();
    if (basePath != null) {
      File currentPath = new File(basePath);
      if (filesEqual(path, currentPath)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static CopyOnWriteArrayList<Project> getNewProjects() {
    return ApplicationManager.getApplication().getUserData(NEW_PROJECTS_KEY);
  }
}
