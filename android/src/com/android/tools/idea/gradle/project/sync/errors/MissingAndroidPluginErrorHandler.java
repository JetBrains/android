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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.MavenRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_URL;
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.searchInBuildFilesOnly;
import static com.android.tools.idea.gradle.util.GradleVersions.isGradle4OrNewer;
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
      if (!hasGoogleMavenRepository(project, gradleBuildModel)) {
        hyperlinks.add(new AddGoogleMavenRepositoryHyperlink(buildFile));
      }
      hyperlinks.add(new OpenFileHyperlink(toSystemDependentName(buildFile.getPath())));
    }
    return hyperlinks;
  }

  private static boolean hasGoogleMavenRepository(@NotNull Project project, @NotNull GradleBuildModel gradleBuildModel) {
    if (isGradle4OrNewer(project) && hasGoogleMavenRepositoryMethod(gradleBuildModel)) {
      // google repository by method can only be used in gradle 4.0+
      return true;
    }
    return hasGoogleMavenRepositoryUrl(gradleBuildModel);
  }

  private static boolean hasGoogleMavenRepositoryMethod(@NotNull GradleBuildModel gradleBuildModel) {
    RepositoriesModel repositories = gradleBuildModel.buildscript().repositories();
    for (RepositoryModel repository : repositories.repositories()) {
      if (repository instanceof GoogleDefaultRepositoryModel) {
        if (GOOGLE_DEFAULT_REPO_NAME.equals(repository.name().value())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasGoogleMavenRepositoryUrl(@NotNull GradleBuildModel gradleBuildModel) {
    RepositoriesModel repositories = gradleBuildModel.buildscript().repositories();
    for (RepositoryModel repository : repositories.repositories()) {
      if (repository instanceof MavenRepositoryModel) {
        MavenRepositoryModel mavenModel = (MavenRepositoryModel)repository;
        if (GOOGLE_DEFAULT_REPO_URL.equals(mavenModel.url().value())) {
          return true;
        }
      }
    }
    return false;
  }
}
