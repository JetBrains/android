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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

public class GradleVersions {
  private static final Pattern GRADLE_JAR_NAME_PATTERN = Pattern.compile("gradle-core-(.*)\\.jar");

  @NotNull private final GradleProjectSettingsFinder mySettingsFinder;

  @NotNull
  public static GradleVersions getInstance() {
    return ServiceManager.getService(GradleVersions.class);
  }

  public GradleVersions(@NotNull GradleProjectSettingsFinder settingsFinder) {
    mySettingsFinder = settingsFinder;
  }

  @Nullable
  public GradleVersion getGradleVersion(@NotNull Project project) {
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (syncState.isSyncNeeded() != ThreeState.YES) {
      // If Sync is needed we cannot rely on the Gradle version returned by the last sync. It may be stale.
      GradleVersion gradleVersion = syncState.getSummary().getGradleVersion();
      if (gradleVersion != null) {
        // The version of Gradle used is retrieved one of the Gradle models. If that fails, we try to deduce it from the project's Gradle
        // settings.
        GradleVersion revision = GradleVersion.tryParse(removeTimestampFromGradleVersion(gradleVersion.toString()));
        if (revision != null) {
          return revision;
        }
      }
    }

    GradleProjectSettings gradleSettings = mySettingsFinder.findGradleProjectSettings(project);
    if (gradleSettings != null) {
      DistributionType distributionType = gradleSettings.getDistributionType();
      if (distributionType == DEFAULT_WRAPPED) {
        GradleWrapper gradleWrapper = GradleWrapper.find(project);
        if (gradleWrapper != null) {
          try {
            String wrapperVersion = gradleWrapper.getGradleVersion();
            if (wrapperVersion != null) {
              return GradleVersion.tryParse(removeTimestampFromGradleVersion(wrapperVersion));
            }
          }
          catch (IOException e) {
            Logger.getInstance(getClass()).info("Failed to read Gradle version in wrapper", e);
          }
        }
      }
      else if (distributionType == LOCAL) {
        String gradleHome = gradleSettings.getGradleHome();
        if (isNotEmpty(gradleHome)) {
          File gradleHomePath = new File(gradleHome);
          return getGradleVersion(gradleHomePath);
        }
      }
    }
    return null;
  }

  /**
   * Attempts to figure out the Gradle version of the given distribution.
   *
   * @param gradleHomePath the path of the directory containing the Gradle distribution.
   * @return the Gradle version of the given distribution, or {@code null} if it was not possible to obtain the version.
   */
  @Nullable
  public GradleVersion getGradleVersion(@NotNull File gradleHomePath) {
    File libFolderPath = new File(gradleHomePath, "lib");
    if (libFolderPath.isDirectory()) {
      for (File child : notNullize(libFolderPath.listFiles())) {
        GradleVersion version = getGradleVersionFromJar(child);
        if (version != null) {
          return version;
        }
      }
    }
    return null;
  }

  @VisibleForTesting
  @Nullable
  static GradleVersion getGradleVersionFromJar(@NotNull File libraryJarFile) {
    String fileName = libraryJarFile.getName();
    Matcher matcher = GRADLE_JAR_NAME_PATTERN.matcher(fileName);
    if (matcher.matches()) {
      // Obtain the version of Gradle from a library name (e.g. "gradle-core-2.0.jar")
      String version = matcher.group(1);
      return GradleVersion.tryParse(removeTimestampFromGradleVersion(version));
    }
    return null;
  }

  @VisibleForTesting
  @NotNull
  public static String removeTimestampFromGradleVersion(@NotNull String gradleVersion) {
    int dashIndex = gradleVersion.indexOf('-');
    if (dashIndex != -1) {
      // in case this is a nightly (e.g. "2.4-20150409092851+0000").
      return gradleVersion.substring(0, dashIndex);
    }
    return gradleVersion;
  }

  /**
   * Verifies if Gradle version used by project is 4.0 or newer
   * @return {@code true} if version is 4.0 or newer, {@code false} if version is lower or it is {@code null}
   */
  public boolean isGradle4OrNewer(@NotNull Project project) {
    GradleVersion gradleVersion = getInstance().getGradleVersion(project);
    return gradleVersion != null && gradleVersion.compareIgnoringQualifiers("4.0") >= 0;
  }
}
