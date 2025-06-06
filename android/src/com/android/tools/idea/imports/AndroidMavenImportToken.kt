/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.DependencyType.IMPLEMENTATION
import com.android.tools.idea.projectsystem.Token
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

interface AndroidMavenImportToken<P : AndroidProjectSystem> : Token {
  /** Returns whether [module] has a (possibly transitive) dependency on [artifact]. */
  fun dependsOn(projectSystem: P, module: Module, artifact: String): Boolean

  /**
   * Registers a new dependency designated by maven [artifact] (with optional [version]), with
   * configuration [type], for [module].
   */
  fun addDependency(
    projectSystem: P,
    module: Module,
    artifact: String,
    version: String?,
    type: DependencyType = IMPLEMENTATION,
  )

  companion object {
    val EP_NAME =
      ExtensionPointName<AndroidMavenImportToken<AndroidProjectSystem>>(
        "com.android.tools.idea.imports.androidMavenImportToken"
      )
  }
}
