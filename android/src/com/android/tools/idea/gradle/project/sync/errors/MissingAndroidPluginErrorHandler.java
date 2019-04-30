/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors;

import static com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink.getBuildFileForPlugin;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenPluginBuildFileHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Process errors caused by not finding Android Gradle plugin
 */
public class MissingAndroidPluginErrorHandler extends BaseSyncErrorHandler {
  private static final String PATTERN = "Could not find com.android.tools.build:gradle:";

  @Nullable
  @Override
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (text.startsWith(PATTERN)) {
      return text;
    }
    return null;
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();

    if (project.isInitialized()) {
      List<VirtualFile> buildFiles = getBuildFileForPlugin(project);
      if (!buildFiles.isEmpty()) {
        VirtualFile buildFile = buildFiles.get(0);
        ProjectBuildModel projectBuildModel = ProjectBuildModel.getOrLog(project);
        if (projectBuildModel != null) {
          GradleBuildModel gradleBuildModel = projectBuildModel.getModuleBuildModel(buildFile);
          // Check if Google Maven repository can be added
          if (!gradleBuildModel.buildscript().repositories().hasGoogleMavenRepository()) {
            hyperlinks.add(new AddGoogleMavenRepositoryHyperlink(ImmutableList.of(buildFile)));
          }
        }
        hyperlinks.add(new OpenFileHyperlink(toSystemDependentName(buildFile.getPath())));
      }
    }
    else {
      // if project is not initialized, offer quickfixes.
      hyperlinks.add(new AddGoogleMavenRepositoryHyperlink(project));
      hyperlinks.add(new OpenPluginBuildFileHyperlink());
    }

    return hyperlinks;
  }
}
