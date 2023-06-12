/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.codenavigation

import com.android.tools.idea.apk.ApkFacet
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import java.nio.file.Paths


internal class ApkMappingNavigable(private val project: Project) : NavSource {
  /**
   * [originalPath] is the path found in a debuggable .so file.
   * [localPath] is the path on the local file system.
   */
  private data class LibraryMapping (val originalPath:String, val localPath:String)

  // Cache the library mapping to avoid re-constructing it foreach location look-up.
  private val libraryMappings: List<LibraryMapping> = getLibraryMappings(project)

  override fun lookUp(location: CodeLocation, arch: String?): Navigatable? {
    if (location.fileName.isNullOrEmpty() || location.lineNumber == CodeLocation.INVALID_LINE_NUMBER) {
      return null
    }

    val fileSystem = LocalFileSystem.getInstance()

    return libraryMappings
      .asSequence()
      .filter { location.fileName!!.startsWith(it.originalPath) }
      .mapNotNull {
        val pathTailAfterPrefix = location.fileName!!.substring(it.originalPath.length)
        val newFileName = Paths.get(it.localPath, pathTailAfterPrefix).toString()
        fileSystem.findFileByPath(newFileName)
      }
      .filter { it.exists() }
      .map { OpenFileDescriptor(project, it, location.lineNumber, 0) }
      .firstOrNull()
  }

  /** Get all the file mappings that connect the library in the APK with a build machine.  */
  private fun getLibraryMappings(project: Project): List<LibraryMapping> {
    // Using a list to preserve order from getSymbolFolderPathMappings and imitate LLDB's behavior.
    val sourceMap: MutableList<LibraryMapping> = ArrayList()

    for (apkFacet in ModuleManager.getInstance(project).modules.mapNotNull { ApkFacet.getInstance(it) }) {
      // getSymbolFolderPathMappings() has a lot of path records which are not mapped, they need
      // to be filtered out.
      sourceMap.addAll(apkFacet.configuration.symbolFolderPathMappings
                         .filter { it.value.isNotEmpty() }
                         .filter { it.value != it.key }
                         .map { LibraryMapping(it.key, it.value) })
    }

    return sourceMap
  }
}