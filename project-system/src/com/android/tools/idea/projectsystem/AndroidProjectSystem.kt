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
   * Uique ID for this type of project system. Each implementation should supply a different
   * id. This will be serialized with the project and should remain stable even if the implementation
   * class name changes. The empty string is reserved for the "default implementation" which will be
   * used if no other project system is applicable. All other implementations must use a non-empty
   * ID string.
   */
  val id: String

  /**
   * Uses build-system-specific heuristics to locate the APK file produced by the given project, or null if none. The heuristics try
   * to determine the most likely APK file corresponding to the application the user is working on in the project's current configuration.
   */
  fun getDefaultApkFile(): VirtualFile?

  /**
   * Returns the absolute filesystem path to the aapt executable being used for the given project.
   */
  fun getPathToAapt(): Path

  /**
   * Initiates an incremental build of the entire project. Blocks the caller until the build
   * is completed.
   *
   * TODO: Make this asynchronous and return something like a ListenableFuture.
   */
  fun buildProject()
}

private val EP_NAME = ExtensionPointName<AndroidProjectSystem>("com.android.project.projectsystem")

/**
 * Returns the instance of {@link AndroidProjectSystem} that applies to the given {@link Project}.
 */
fun getInstance(project: Project): AndroidProjectSystem {
  return project.getComponent(ProjectSystemComponent::class.java).projectSystem
}

internal fun detectProjectSystem(project: Project): AndroidProjectSystem {
  val extensions = EP_NAME.getExtensions(project)
  return extensions.find { it.isApplicable() }
      ?: extensions.find { it.id == "" }
      ?: throw IllegalStateException("Default AndroidProjectSystem not found for project " + project.name)
}
