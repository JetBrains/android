/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.utils.FileUtils;
import java.io.File;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.jetbrains.annotations.NotNull;

public final class Modules {
  private Modules() {
  }

  @NotNull
  public static String createUniqueModuleId(@NotNull BasicGradleProject gradleProject) {
    File rootProjectFolderPath = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir();
    return createUniqueModuleId(rootProjectFolderPath.getPath(), gradleProject.getPath());
  }

  /**
   * This method creates a unique string identifier for a module, the value is project id plus Gradle path.
   * For example: "/path/to/project1:lib", or "/path/to/project1:lib1".
   *
   * @param rootProjectFolderPath path to project root folder.
   * @param gradlePath            Gradle path of a module.
   * @return a unique identifier for a module, i.e. project folder path + Gradle path.
   */
  @NotNull
  public static String createUniqueModuleId(@NotNull File rootProjectFolderPath, @NotNull String gradlePath) {
    return createUniqueModuleId(rootProjectFolderPath.getPath(), gradlePath);
  }

  /**
   * This method creates a unique string identifier for a module, the value is project id plus Gradle path.
   * For example: "/path/to/project1:lib", or "/path/to/project1:lib1".
   *
   * @param rootProjectFolderPath path to project root folder.
   * @param gradlePath            Gradle path of a module.
   * @return a unique identifier for a module, i.e. system-dependent project folder path + Gradle path.
   */
  @NotNull
  public static String createUniqueModuleId(@NotNull String rootProjectFolderPath, @NotNull String gradlePath) {
    return FileUtils.toSystemDependentPath(rootProjectFolderPath) + ':' + gradlePath;
  }

  /**
   * This method creates a unique string identifier for a GradleProject, the value is project id plus Gradle path.
   * For example: "/path/to/project1:lib", or "/path/to/project1:lib1".
   *
   * @return a unique identifier for GradleProject, i.e. system-dependent project folder path + Gradle path.
   */
  @NotNull
  public static String createUniqueModuleId(@NotNull GradleProject gradleProject) {
    File rootProjectFolderPath = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir();
    return createUniqueModuleId(rootProjectFolderPath.getPath(), gradleProject.getPath());
  }
}
