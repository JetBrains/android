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
package com.android.tools.idea.projectsystem

import java.io.File

/**
 * Represents a template for creating new Android components. It knows where to put the various
 * files given the root of the module. This is used when creating new files:
 * E.g., New Activity, Fragment, Module, Project.
 */
interface AndroidModulePaths {
  val moduleRoot: File?

  /**
   * @param packageName package name of the new component. May affect resulting source directory (e.g., appended to source root).
   * For "com.google.foo.Bar", this would be "com.google.foo", and the resulting source directory *might* be
   * "src/main/java/com/google/foo/".
   * null if no transformation is required on the source root.
   * @return the target directory in which to place a new Android component.
   */
  fun getSrcDirectory(packageName: String?): File?

  /**
   * Similar to [AndroidModulePaths.getSrcDirectory], except for new tests.
   */
  fun getTestDirectory(packageName: String?): File?

  /**
   * Resource directories in order of increasing precedence. A resource in the last directory overrides
   * resources with the same name and type in earlier directories.
   */
  val resDirectories: List<File>

  /**
   * Similar to [AndroidModulePaths.getSrcDirectory], except for new aidl files.
   */
  fun getAidlDirectory(packageName: String?): File?

  val manifestDirectory: File?
}
