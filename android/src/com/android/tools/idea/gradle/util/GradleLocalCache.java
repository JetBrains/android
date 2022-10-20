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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import org.gradle.initialization.BuildLayoutParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.Collections.sort;

public class GradleLocalCache {
  @NotNull
  public static GradleLocalCache getInstance() {
    return ApplicationManager.getApplication().getService(GradleLocalCache.class);
  }

  @Nullable
  public GradleVersion findLatestArtifactVersion(@NotNull GradleCoordinate artifactCoordinate,
                                                 @Nullable Project project,
                                                 @Nullable String versionPrefix) {
    String groupId = artifactCoordinate.getGroupId();
    String artifactId = artifactCoordinate.getArtifactId();
    if (isNotEmpty(groupId) && isNotEmpty(artifactId)) {
      return findLatestArtifactVersion(groupId, artifactId, project, versionPrefix);
    }
    return null;
  }

  @Nullable
  public GradleVersion findLatestArtifactVersion(@NotNull String groupId,
                                                 @NotNull String artifactId,
                                                 @Nullable Project project,
                                                 @Nullable String versionPrefix) {
    for (File gradleServicePath : getGradleServicePaths(project)) {
      GradleVersion version = findLatestVersionInGradleCache(gradleServicePath, groupId, artifactId, versionPrefix);
      if (version != null) {
        return version;
      }
    }
    return null;
  }

  @Nullable
  private static GradleVersion findLatestVersionInGradleCache(@NotNull File gradleServicePath,
                                                              @NotNull String groupId,
                                                              @NotNull String artifactId,
                                                              @Nullable String versionPrefix) {
    File gradleCacheFolder = new File(gradleServicePath, "caches");
    if (!gradleCacheFolder.isDirectory()) {
      return null;
    }
    List<GradleVersion> versions = new ArrayList<>();
    for (File moduleFolder : notNullize(gradleCacheFolder.listFiles())) {
      if (!isDirectoryWithNamePrefix(moduleFolder, "modules-")) {
        continue;
      }
      for (File metadataFolder : notNullize(moduleFolder.listFiles())) {
        if (!isDirectoryWithNamePrefix(metadataFolder, "metadata-")) {
          continue;
        }
        File versionFolder = new File(metadataFolder, join("descriptors", groupId, artifactId));
        if (!versionFolder.isDirectory()) {
          continue;
        }
        for (File versionFile : notNullize(versionFolder.listFiles())) {
          String version = versionFile.getName();
          if ((versionPrefix == null || version.startsWith(versionPrefix)) && !version.isEmpty() && Character.isDigit(version.charAt(0))) {
            GradleVersion parsedVersion = GradleVersion.tryParse(version);
            if (parsedVersion != null) {
              versions.add(parsedVersion);
            }
          }
        }
      }
    }
    int versionCount = versions.size();
    if (versionCount == 1) {
      return versions.get(0);
    }
    else if (versionCount > 1) {
      sort(versions);
      return versions.get(versionCount - 1);
    }
    return null;
  }

  private static boolean isDirectoryWithNamePrefix(@NotNull File file, @NotNull String prefix) {
    return file.getName().startsWith(prefix) && file.isDirectory();
  }

  public boolean containsGradleWrapperVersion(@NotNull String gradleVersion, @NotNull Project project) {
    String distFolderName = "gradle-" + gradleVersion;
    String wrapperDirNamePrefix = distFolderName + "-";

    // Try both distributions "all" and "bin".
    String[] wrapperFolderNames = {wrapperDirNamePrefix + "all", wrapperDirNamePrefix + "bin"};

    for (File gradleServicePath : getGradleServicePaths(project)) {
      for (String wrapperFolderName : wrapperFolderNames) {
        File wrapperFolderPath = new File(gradleServicePath, join("wrapper", "dists", wrapperFolderName));
        if (!wrapperFolderPath.isDirectory()) {
          continue;
        }
        // There is a folder that contains the actual distribution
        // Example: // ~/.gradle/wrapper/dists/gradle-2.1-all/27drb4udbjf4k88eh2ffdc0n55/gradle-2.1
        for (File mayBeDistParent : notNullize(wrapperFolderPath.listFiles())) {
          if (!mayBeDistParent.isDirectory()) {
            continue;
          }
          for (File mayBeDistFolder : notNullize(mayBeDistParent.listFiles())) {
            if (mayBeDistFolder.isDirectory() && distFolderName.equals(mayBeDistFolder.getName())) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  @NotNull
  private static Collection<File> getGradleServicePaths(@Nullable Project project) {
    Set<File> paths = new LinkedHashSet<>();
    if (project != null) {
      // Use the one set in the IDE
      GradleSettings settings = GradleSettings.getInstance(project);
      String path = settings.getServiceDirectoryPath();
      if (isNotEmpty(path)) {
        File file = new File(path);
        if (file.isDirectory()) {
          paths.add(file);
        }
      }
    }
    // The default location
    File path = new BuildLayoutParameters().getGradleUserHomeDir();
    if (path.isDirectory()) {
      paths.add(path);
    }
    return paths;
  }
}
