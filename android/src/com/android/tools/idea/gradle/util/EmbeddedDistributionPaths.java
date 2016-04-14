/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class EmbeddedDistributionPaths {
  private static final Logger LOG = Logger.getInstance(EmbeddedDistributionPaths.class);

  @Nullable
  public static File findAndroidStudioLocalMavenRepoPath() {
    File defaultRootDirPath = getDefaultRootDirPath();
    File repoPath;
    if (defaultRootDirPath != null) {
      // Release build
      repoPath = new File(defaultRootDirPath, "m2repository");
    }
    else {
      // Development build
      String studioCustomRepo = System.getenv("STUDIO_CUSTOM_REPO");
      if (studioCustomRepo != null) {
        repoPath = new File(toCanonicalPath(toSystemDependentName(studioCustomRepo)));
        if (!repoPath.isDirectory()) {
          throw new IllegalArgumentException("Invalid path in STUDIO_CUSTOM_REPO environment variable");
        }
      } else {
        String relativePath = toSystemDependentName("/../../prebuilts/tools/common/offline-m2");
        repoPath = new File(toCanonicalPath(toSystemDependentName(PathManager.getHomePath()) + relativePath));
      }
    }
    LOG.info("Looking for embedded Maven repo at '" + repoPath.getPath() + "'");
    return repoPath.isDirectory() ? repoPath : null;
  }

  @Nullable
  public static File findEmbeddedGradleDistributionPath() {
    File defaultRootDirPath = getDefaultRootDirPath();
    if (defaultRootDirPath != null) {
      // Release build
      File embeddedPath = new File(defaultRootDirPath, "gradle-" + GRADLE_LATEST_VERSION);
      LOG.info("Looking for embedded Gradle distribution at '" + embeddedPath.getPath() + "'");
      if (embeddedPath.isDirectory()) {
        LOG.info("Found embedded Gradle " + GRADLE_LATEST_VERSION);
        return embeddedPath;
      }
      LOG.info("Unable to find embedded Gradle " + GRADLE_LATEST_VERSION);
      return null;
    }
    // For development build, we should have Gradle installed.
    return null;
  }

  @Nullable
  private static File getDefaultRootDirPath() {
    String ideHomePath = toSystemDependentName(PathManager.getHomePath());
    File rootDirPath = new File(ideHomePath, "gradle");
    return rootDirPath.isDirectory() ? rootDirPath : null;
  }
}
