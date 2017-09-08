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
@file:JvmName("BuildSystemServiceUtil")

package com.android.tools.idea.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Handles generic build system operations such as syncing and building. Implementations of this interface will
 * receive a {@link Project} instance in their constructor.
 */
interface BuildSystemService {

  /**
   * Returns true iff this object is applicable to the {@link Project} it was created on.
   * This method is called immediately after construction. If it returns false, the instance
   * is discarded and no further methods are invoked.
   */
  fun isApplicable(): Boolean

  /**
   * Adds a dependency to the given module.
   *
   * TODO: Figure out and document the format for the dependency strings
   */
  fun addDependency(module: Module, dependency: String)

  /**
   * Merge new dependencies into a (potentially existing) build file. Build files are build-system-specific
   * text files describing the steps for building a single android application or library.
   *
   * TODO: The association between a single android library and a single build file is too gradle-specific.
   * TODO: Document the exact format for the supportLibVersionFilter string
   * TODO: Document the format for the dependencies string
   *
   * @param dependencies new dependencies.
   * @param destinationContents original content of the build file.
   * @param supportLibVersionFilter If a support library filter is provided, the support libraries will be
   * limited to match that filter. This is typically set to the compileSdkVersion, such that you don't end
   * up mixing and matching compileSdkVersions and support libraries from different versions, which is not
   * supported.
   *
   * @return new content of the build file
   */
  fun mergeBuildFiles(dependencies: String,
                      destinationContents: String,
                      supportLibVersionFilter: String?): String

}

val EP_NAME = ExtensionPointName<BuildSystemService>("com.android.project.buildSystemService")

fun getInstance(project: Project): BuildSystemService? {
  return EP_NAME.getExtensions(project).find { it.isApplicable() }
}