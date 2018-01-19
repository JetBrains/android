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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.EnableEmbeddedRepoHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.searchInBuildFilesOnly;
import static com.android.tools.idea.gradle.project.sync.errors.MissingDependencyErrorHandler.MISSING_DEPENDENCY_PATTERN;
import static com.android.tools.idea.gradle.project.sync.hyperlink.EnableEmbeddedRepoHyperlink.shouldEnableEmbeddedRepo;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

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

    // Get Android Plugin info from the project, if plugin info can not be found, use project build.gradle file instead
    AndroidPluginInfo result = searchInBuildFilesOnly(project);
    GradleBuildModel gradleBuildModel = null;
    if (result != null) {
      VirtualFile buildFile = result.getPluginBuildFile();
      if (buildFile != null) {
        gradleBuildModel = GradleBuildModel.parseBuildFile(buildFile, project);
      }
    }
    if (gradleBuildModel == null) {
      gradleBuildModel = GradleBuildModel.get(project);
    }
    if (gradleBuildModel != null) {
      // Check if Google Maven repository can be added
      VirtualFile buildFile = gradleBuildModel.getVirtualFile();
      if (!gradleBuildModel.buildscript().repositories().hasGoogleMavenRepository()) {
        hyperlinks.add(new AddGoogleMavenRepositoryHyperlink(buildFile));
      }
      hyperlinks.add(new OpenFileHyperlink(toSystemDependentName(buildFile.getPath())));
    }

    // Offer to turn on embedded offline repo if the missing Android plugin can be found there.
    Matcher matcher = MISSING_DEPENDENCY_PATTERN.matcher(getFirstLineMessage(text));
    if (matcher.matches()) {
      if (shouldEnableEmbeddedRepo(matcher.group(1))) {
        hyperlinks.add(new EnableEmbeddedRepoHyperlink());
      }
    }
    return hyperlinks;
  }
}
