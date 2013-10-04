/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods related to Gradle versions.
 *
 * This class will be removed once we set the minimum supported Gradle version to 1.8. Right now we support Gradle 1.6, 1.7 and 1.8. We
 * need to know the Gradle version in order to use the new "single-pass" project import mechanism introduced in Gradle 1.8.
 */
final class GradleVersions {
  private static final Pattern GRADLE_HOME_DIR_PATTERN = Pattern.compile("gradle-(\\d.[\\d]+)(-.*)?");
  private static final Pattern GRADLE_JAR_FILE_PATTERN = Pattern.compile("gradle-core-(\\d.[\\d]+)(-.*)?\\.jar");

  private GradleVersions() {
  }

  /**
   * Obtains the version of Gradle installed at the given directory.
   *
   * @param gradleHomeDir the path of the Gradle home directory.
   * @return the version of Gradle installed at the given directory, or {@code null} the given directory does not have a Gradle
   *         installation.
   */
  @Nullable
  static String getGradleVersion(@NotNull File gradleHomeDir) {
    // Try to obtain Gradle version from the Gradle home directory's name.
    Matcher matcher = GRADLE_HOME_DIR_PATTERN.matcher(gradleHomeDir.getName());
    if (matcher.matches()) {
      return matcher.group(1);
    }
    // If the home directory has a custom name, check the jars in the "lib" folder.
    File libDir = new File(gradleHomeDir, "lib");
    if (libDir.isDirectory()) {
      File[] children = FileUtil.notNullize(libDir.listFiles());
      for (File file : children) {
        if (file.isFile()) {
          //noinspection TestOnlyProblems
          String version = getGradleVersionFromJarFile(file);
          if (version != null) {
            return version;
          }
        }
      }
    }
    return null;
  }

  @VisibleForTesting
  @Nullable
  static String getGradleVersionFromJarFile(@NotNull File jarFile) {
    Matcher matcher = GRADLE_JAR_FILE_PATTERN.matcher(jarFile.getName());
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }
}
