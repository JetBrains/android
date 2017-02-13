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
package com.android.tools.idea.gradle.project.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.BUILD_DIR_DEFAULT_NAME;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.project.ProjectTypeService.setProjectType;
import static com.intellij.openapi.util.io.FileUtil.join;

public class NewProjectSetup {
  public static final ProjectType ANDROID_PROJECT_TYPE = new ProjectType("Android");

  @NotNull
  public Project createProject(@NotNull String projectName, @NotNull String projectPath) throws ConfigurationException {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project newProject = projectManager.createProject(projectName, projectPath);
    if (newProject == null) {
      throw new NullPointerException("Failed to create a new project");
    }
    return newProject;
  }

  @NotNull
  Project openProject(@NotNull String projectPath) throws IOException {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project newProject = projectManager.loadProject(projectPath);
    if (newProject == null) {
      throw new NullPointerException("Failed to open project at '" + projectPath + "'");
    }
    return newProject;
  }

  void prepareProjectForImport(@NotNull Project project, @Nullable LanguageLevel languageLevel) {
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (languageLevel != null) {
        LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(project);
        if (extension != null) {
          extension.setLanguageLevel(languageLevel);
        }
      }

      // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
      // IDEA.
      File compilerOutputDirPath = new File(getBaseDirPath(project), join(BUILD_DIR_DEFAULT_NAME, "classes"));
      String compilerOutputDirUrl = pathToIdeaUrl(compilerOutputDirPath);
      CompilerProjectExtension compilerProjectExt = CompilerProjectExtension.getInstance(project);
      assert compilerProjectExt != null;
      compilerProjectExt.setCompilerOutputUrl(compilerOutputDirUrl);

      // This allows to customize UI when android project is opened inside IDEA with android plugin.
      setProjectType(project, ANDROID_PROJECT_TYPE);
    }), null, null);
  }
}
