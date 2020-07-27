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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleProjects.open;
import static com.android.tools.idea.gradle.util.GradleUtil.BUILD_DIR_DEFAULT_NAME;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.util.ToolWindows.activateProjectView;
import static com.intellij.openapi.application.TransactionGuard.submitTransaction;
import static com.intellij.openapi.project.ProjectTypeService.setProjectType;
import static com.intellij.openapi.util.io.FileUtil.join;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NewProjectSetup {
  public static final ProjectType ANDROID_PROJECT_TYPE = new ProjectType("Android");

  @NotNull private final TopLevelModuleFactory myTopLevelModuleFactory;

  public NewProjectSetup() {
    this(new TopLevelModuleFactory(IdeInfo.getInstance(), IdeSdks.getInstance()));
  }

  @VisibleForTesting
  NewProjectSetup(@NotNull TopLevelModuleFactory topLevelModuleFactory) {
    myTopLevelModuleFactory = topLevelModuleFactory;
  }

  public @NotNull Project createProject(@NotNull String projectName, @NotNull Path projectPath) {
    Project newProject = ProjectManagerEx.getInstanceEx().newProject(projectPath, OpenProjectTask.newProject().withProjectName(projectName));
    if (newProject == null) {
      throw new NullPointerException("Failed to create a new project");
    }

    Sdk projectJdk = IdeSdks.getInstance().getJdk();
    if (projectJdk != null) {
      ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(newProject).setProjectSdk(projectJdk));
    }

    return newProject;
  }

  void prepareProjectForImport(@NotNull Project project, @Nullable LanguageLevel languageLevel) {
    submitTransaction(project, () -> openProjectAndActivateProjectView(project));
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null) {
        ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(project).setProjectSdk(jdk));
      }

      if (languageLevel != null) {
        LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(project);
        if (extension != null) {
          extension.setLanguageLevel(languageLevel);
        }
      }

      // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
      // IDEA.
      File compilerOutputFolderPath = new File(getBaseDirPath(project), join(BUILD_DIR_DEFAULT_NAME, "classes"));
      String compilerOutputFolderUrl = pathToIdeaUrl(compilerOutputFolderPath);
      CompilerProjectExtension compilerProjectExt = CompilerProjectExtension.getInstance(project);
      assert compilerProjectExt != null;
      compilerProjectExt.setCompilerOutputUrl(compilerOutputFolderUrl);

      // This allows to customize UI when android project is opened inside IDEA with android plugin.
      setProjectType(project, ANDROID_PROJECT_TYPE);
    }), null, null);
  }

  private void openProjectAndActivateProjectView(@NotNull Project project) {
    ApplicationManager.getApplication().runWriteAction(() -> myTopLevelModuleFactory.createTopLevelModule(project));

    // Just by opening the project, Studio will show the error message in a balloon notification, automatically.
    open(project);

    // Activate "Project View" so users don't get an empty window.
    activateProjectView(project);
  }
}
