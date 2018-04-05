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

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class Modules {
  private Modules() {
  }

  /**
   * This method creates a unique string identifier for a module, the value is project id plus Gradle path.
   * For example: "/path/to/project1:lib", or "/path/to/project1:lib1".
   *
   * @param folderPath path to project root folder.
   * @param gradlePath gradle path of a module.
   * @return a unique identifier for a module, i.e. project folder path + Gradle path.
   */
  @NotNull
  public static String createUniqueModuleId(@NotNull File folderPath, @NotNull String gradlePath) {
    return createUniqueModuleId(folderPath.getPath(), gradlePath);
  }

  /**
   * This method creates a unique string identifier for a module, the value is project id plus Gradle path.
   * For example: "/path/to/project1:lib", or "/path/to/project1:lib1".
   *
   * @param folderPath path to project root folder.
   * @param gradlePath gradle path of a module.
   * @return a unique identifier for a module, i.e. project folder path + Gradle path.
   */
  @NotNull
  public static String createUniqueModuleId(@NotNull String folderPath, @NotNull String gradlePath) {
    return folderPath + ':' + gradlePath;
  }
}
