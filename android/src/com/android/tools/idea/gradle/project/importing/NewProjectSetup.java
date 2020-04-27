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
import static com.android.tools.idea.gradle.util.GradleUtil.BUILD_DIR_DEFAULT_NAME;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.invokeLater;
import static com.intellij.openapi.project.ProjectTypeService.setProjectType;
import static com.intellij.openapi.util.io.FileUtil.join;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.util.ToolWindows;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewProjectSetup {
  public static final ProjectType ANDROID_PROJECT_TYPE = new ProjectType("Android");

  @NotNull private final TopLevelModuleFactory myTopLevelModuleFactory;

  public NewProjectSetup() {
    this(new TopLevelModuleFactory(IdeInfo.getInstance(), IdeSdks.getInstance()));
  }

  @VisibleForTesting
  NewProjectSetup(@NotNull TopLevelModuleFactory topLevelModuleFactory) {
    myTopLevelModuleFactory = topLevelModuleFactory;
  }

  void prepareProjectForImport(@NotNull Project project, @Nullable LanguageLevel languageLevel) {
    WriteAction.runAndWait(() -> {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null) {
        ProjectRootManager.getInstance(project).setProjectSdk(jdk);
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
      myTopLevelModuleFactory.createTopLevelModule(project);
    });
    invokeLater(project, () -> ToolWindows.activateProjectView(project));
  }
}
