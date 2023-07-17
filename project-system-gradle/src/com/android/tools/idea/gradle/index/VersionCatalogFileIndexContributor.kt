/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.index

import com.android.tools.idea.gradle.dsl.utils.EXT_VERSIONS_TOML
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.iterateChildrenRecursively
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.isFile
import com.intellij.util.indexing.IndexableSetContributor
import java.io.File

/**
 * Contributor iterates and add .versions.toml files from gradle folder,
 * ignoring subdirectories.
 */
class VersionCatalogFileIndexContributor : IndexableSetContributor() {
  companion object {
    private const val GRADLE_FOLDER = "gradle"
  }
  override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> {
    val versionsTomlFilter = VirtualFileFilter { file ->
      (file.isDirectory && file.name == GRADLE_FOLDER) || file.name.endsWith(EXT_VERSIONS_TOML)
    }
    val result = mutableSetOf<VirtualFile>()
    LocalFileSystem.getInstance().findFileByIoFile(
      File(project.basePath, GRADLE_FOLDER)
    )?.let {
      iterateChildrenRecursively(it, versionsTomlFilter) { file ->
        if (file.isFile) result.add(file)
        true
      }
    }
    return result
  }

  override fun getAdditionalRootsToIndex(): Set<VirtualFile> = emptySet()

  override fun getDebugName(): String {
    return "Version Catalogs"
  }
}