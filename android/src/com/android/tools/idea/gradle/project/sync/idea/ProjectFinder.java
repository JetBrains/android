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

import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.io.FilePaths;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;

public class ProjectFinder {
  @Nullable
  private Project findProject(@NotNull String projectPath) {
    if (isNotEmpty(projectPath)) {
      File projectFolderPath = FilePaths.stringToFile(projectPath);
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        if (hasMatchingPath(project, projectFolderPath)) {
          return project;
        }
      }
    }
    return null;
  }


  @Nullable
  Project findProject(@NotNull ProjectResolverContext context) {
    String projectPath = context.getProjectPath();
    return findProject(projectPath);
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
}
