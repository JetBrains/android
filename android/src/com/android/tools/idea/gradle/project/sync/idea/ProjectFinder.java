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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;

import java.io.File;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

class ProjectFinder {
  @Nullable
  Project findProject(@NotNull ProjectResolverContext context) {
    String projectPath = context.getProjectPath();
    if (isNotEmpty(projectPath)) {
      File projectDirPath = toSystemDependentPath(projectPath);
      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (Project project : projects) {
        String basePath = project.getBasePath();
        if (basePath != null) {
          File currentPath = new File(basePath);
          if (filesEqual(projectDirPath, currentPath)) {
            return project;
          }
        }
      }
    }
    return null;
  }
}
