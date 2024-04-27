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
package com.android.tools.idea.gradle.util;

import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings.CompositeBuild;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class BuildFileProcessor {
  /**
   * Attempts to find and process (using the supplied processors) all build files in the given project.
   *
   * The supplied processors will never be given null by this method.
   *
   * This method parses the settings file, processes it, then parses build files; as such it is not guaranteed to catch every file.
   * If finding every file is required look at getting the build files from the model, see {@link GradleModuleModel#getBuildFile()}.
   */
  public static void processRecursively(@NotNull Project project,
                                        @NotNull Processor<? super GradleSettingsModel> settingsProcessor,
                                        @NotNull Processor<? super GradleBuildModel> buildProcessor) {
    ApplicationManager.getApplication().runReadAction(() -> {
      VirtualFile projectRootFolder = ProjectUtil.guessProjectDir(project);
      // Unlikely to happen; if it does, most likely this is the default project.
      if (projectRootFolder == null) return;

      ProjectBuildModel projectBuildModel = ProjectBuildModel.getOrLog(project);
      // Something went wrong.
      if (projectBuildModel == null) return;

      GradleSettingsModel settings = GradleBuildModel.tryOrLog(() -> projectBuildModel.getProjectSettingsModel());
      // without a GradleSettingsModel we assume nothing about the project.
      if (settings == null) return;

      boolean continueProcessing = settingsProcessor.process(settings);
      if (!continueProcessing) return;

      for (String path : settings.modulePaths()) {
        GradleBuildModel buildModel = GradleBuildModel.tryOrLog(() -> settings.moduleModel(path));
        if (buildModel == null) continue;
        continueProcessing = buildProcessor.process(buildModel);
        if (!continueProcessing) return;
      }
    });
  }

  /**
   * Returns a list of root folders of the composite projects.
   */
  @NotNull
  public static List<File> getCompositeBuildFolderPaths(@NotNull Project project) {
    String projectBasePath = project.getBasePath();
    if (isEmpty(projectBasePath)) {
      return Collections.emptyList();
    }

    GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(projectBasePath);
    if (projectSettings == null) {
      return Collections.emptyList();
    }

    CompositeBuild compositeBuild = projectSettings.getCompositeBuild();
    if (compositeBuild == null) {
      return Collections.emptyList();
    }

    return compositeBuild.getCompositeParticipants().stream()
      .map(p -> new File(toSystemDependentPath(p.getRootPath())))
      .collect(Collectors.toList());
  }
}
