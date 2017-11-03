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
package com.android.tools.idea.gradle.project.model.ide.android.level2;

import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The "build" folder paths per module.
 */
public class BuildFolderPaths {
  // Key: Module's Gradle path. Value: Path of the module's 'build' folder.
  @NotNull private final Map<String, File> myBuildFolderPathsByModule = new HashMap<>();

  /**
   * Extracts and stores the "build" folder path from the given {@link GradleProject}.
   *
   * @param gradleProject the given {@code GradleProject}.
   */
  public void add(@NotNull GradleProject gradleProject) {
    try {
      myBuildFolderPathsByModule.put(gradleProject.getPath(), gradleProject.getBuildDirectory());
    }
    catch (UnsupportedMethodException exception) {
      // getBuildDirectory is available for Gradle versions older than 2.0.
      // For older versions of gradle, there's no way to get build directory.
    }
  }

  @TestOnly
  public void addBuildFolderMapping(@NotNull String moduleGradlePath, @NotNull String buildFolderPath) {
    myBuildFolderPathsByModule.put(moduleGradlePath, new File(buildFolderPath));
  }

  /**
   * Finds the path of the "build" folder for the given module path.
   *
   * @param moduleGradlePath the given module path.
   * @return the path of the "build" folder for the given module path; or {@code null} if the path is not found or haven't been registered
   * yet.
   */
  @Nullable
  public File findBuildFolderPath(@NotNull String moduleGradlePath) {
    return myBuildFolderPathsByModule.get(moduleGradlePath);
  }
}
