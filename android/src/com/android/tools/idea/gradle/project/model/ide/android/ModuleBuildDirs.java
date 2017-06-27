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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.annotations.VisibleForTesting;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds a map from project path to build directory for all modules.
 */
public class ModuleBuildDirs {
  @NotNull private final Map<String, File> myProjectPathToBuildDir;

  public ModuleBuildDirs() {
    this(new HashMap<>());
  }

  @VisibleForTesting
  ModuleBuildDirs(@NotNull Map<String, File> projectPathToBuildDir) {
    myProjectPathToBuildDir = projectPathToBuildDir;
  }

  /**
   * Add build directory of the given GradleProject.
   *
   * @param gradleProject GradleProject of the module.
   */
  public void add(@NotNull GradleProject gradleProject) {
    try {
      myProjectPathToBuildDir.put(gradleProject.getPath(), gradleProject.getBuildDirectory());
    }
    catch (UnsupportedMethodException exception) {
      // getBuildDirectory is available for Gradle versions older than 2.0.
      // For older versions of gradle, there's no way to get build directory.
    }
  }

  /**
   * Get build directory of the given module.
   *
   * @param projectPath Path of the module.
   * @return Build directory. Returns null if the module doesn't exist.
   */
  @Nullable
  public File getBuildDir(@NotNull String projectPath) {
    return myProjectPathToBuildDir.get(projectPath);
  }
}
