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
@file:JvmName("ProjectSystemUtil")
package com.android.tools.idea.projectsystem

import com.intellij.openapi.project.Project
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Interface to be implemented by extensions to the com.android.tools.idea.projectsystem extension
 * point. Implementations of this interface will receive a {@link Project} instance in their constructor.
 */
interface AndroidProjectSystem {
  /**
   * Returns true if this instance is applicable to the project. Note that {@link AndroidProjectSystem}
   * instances may be constructed for projects they do not apply to. Such instances should return false
   * from this method, and they will be discarded.
   */
  fun isApplicable(): Boolean

  /**
   * Uses build-system-specific heuristics to locate the APK file produced by the given project, or null if none. The heuristics try
   * to determine the most likely APK file corresponding to the application the user is working on in the project's current configuration.
   */
  fun getDefaultApkFile(): VirtualFile?

  /**
   * Returns the absolute filesystem path to the aapt executable being used for the given project.
   */
  fun getPathToAapt(): Path
}

private val EP_NAME = ExtensionPointName<AndroidProjectSystem>("com.android.project.projectsystem")

/**
 * Returns the instance of {@link AndroidProjectSystem} that applies to the given {@link Project}.
 * Throws an exception if none.
 */
fun getInstance(project: Project): AndroidProjectSystem {
  return EP_NAME.getExtensions(project).find { it.isApplicable() }?:
      throw IllegalStateException("No AndroidProjectSystem found for project " + project.name)
}
