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
package com.android.tools.idea.gradle.catalog

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformIcons
import javax.swing.Icon
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles

/**
 * Provides imported version catalogs (those located in Gradle cache) as synthetic libraries to be displayed in the External Libraries node
 * of the Project View.
 */
class GradleVersionCatalogLibraryRootsProvider : AdditionalLibraryRootsProvider() {
  override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
    val basePath = project.basePath ?: return emptyList()
    val projectDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()

    val modules = ModuleManager.getInstance(project).modules
    val catalogs = modules.flatMap { module -> getVersionCatalogFiles(module).entries }.distinctBy { it.value }

    return catalogs.mapNotNull { (name, file) ->
      // Only show catalogs that are outside the project content (e.g. in Gradle cache)
      if (!VfsUtilCore.isAncestor(projectDir, file, false)) {
        GradleVersionCatalogSyntheticLibrary(name, file)
      } else {
        null
      }
    }
  }

  override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
    return emptyList()
  }
}

private class GradleVersionCatalogSyntheticLibrary(val name: String, val file: VirtualFile) : SyntheticLibrary(), ItemPresentation {

  override fun getSourceRoots(): Collection<VirtualFile> = listOf(file)

  override fun getPresentableText(): String = "Imported Catalog: $name"

  override fun getLocationString(): String? = null

  override fun getIcon(unused: Boolean): Icon = PlatformIcons.LIBRARY_ICON

  override fun isShowInExternalLibrariesNode(): Boolean = true

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleVersionCatalogSyntheticLibrary) return false
    return file == other.file
  }

  override fun hashCode(): Int = file.hashCode()
}
