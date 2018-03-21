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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings.CompositeBuild;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.dsl.api.GradleBuildModel.parseBuildFile;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtil.processFileRecursivelyWithoutIgnored;

public class BuildFileProcessor {
  @NotNull
  public static BuildFileProcessor getInstance() {
    return ServiceManager.getService(BuildFileProcessor.class);
  }

  public void processRecursively(@NotNull Project project,
                                 @NotNull Processor<GradleBuildModel> processor,
                                 boolean processCompositeBuilds) {
    ApplicationManager.getApplication().runReadAction(() -> {
      VirtualFile projectRootFolder = project.getBaseDir();
      if (projectRootFolder == null) {
        // Unlikely to happen: this is default project.
        return;
      }

      List<VirtualFile> projectRootFolders = new ArrayList<>();
      projectRootFolders.add(projectRootFolder);
      if (processCompositeBuilds) {
        List<File> compositeBuildFolders = getCompositeBuildFolderPaths(project);
        projectRootFolders.addAll(compositeBuildFolders.stream()
                                    .map(file -> findFileByIoFile(file, true /* refresh if needed */))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()));
      }

      for (VirtualFile rootFolder : projectRootFolders) {
        processFileRecursivelyWithoutIgnored(rootFolder, virtualFile -> {
          if (FN_BUILD_GRADLE.equals(virtualFile.getName())) {
            GradleBuildModel buildModel = parseBuildFile(virtualFile, project);
            return processor.process(buildModel);
          }
          return true;
        });
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
