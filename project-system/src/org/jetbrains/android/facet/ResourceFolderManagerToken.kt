/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.facet

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

interface ResourceFolderManagerToken<P : AndroidProjectSystem>: Token {
  /**
   * Return the list of resource folders for this [Module] and its associated [SourceProviders] given the
   * specific [AndroidProjectSystem], or `null` if the default behavior in [defaultFoldersFromSourceProviders]
   * is correct for this situation.
   */
  fun computeFoldersFromSourceProviders(projectSystem: P, sourceProviders: SourceProviders, module: Module): List<VirtualFile>?

  companion object {
    val EP_NAME = ExtensionPointName<ResourceFolderManagerToken<AndroidProjectSystem>>(
      "org.jetbrains.android.facet.resourceFolderManagerToken")

    private fun defaultFoldersFromSourceProviders(sourceProviders: SourceProviders) = sourceProviders.run {
      (currentSourceProviders.flatMap { it.resDirectories } + generatedSources.resDirectories).toList()
    }

    fun computeFoldersFromSourceProviders(sourceProviders: SourceProviders, module: Module): List<VirtualFile> {
      val projectSystem = module.project.getProjectSystem()
      return projectSystem.getTokenOrNull(EP_NAME)?.computeFoldersFromSourceProviders(projectSystem, sourceProviders, module)
             ?: defaultFoldersFromSourceProviders(sourceProviders)
    }
  }
}