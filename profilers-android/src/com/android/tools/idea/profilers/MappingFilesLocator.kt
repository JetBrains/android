/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path

fun interface R8MappingSource {
  fun getMappings(): List<ProfilerR8MappingToken.R8Mapping>
}

class ProjectR8MappingSource(project: Project) : R8MappingSource {
  private val projectRef = WeakReference(project)

  override fun getMappings(): List<ProfilerR8MappingToken.R8Mapping> {
    val project = projectRef.get()?.takeIf { !it.isDisposed } ?: return emptyList()
    return runReadActionBlocking {
      ProfilerR8MappingToken.getR8Mappings(project)
    }
  }
}

class MappingFilesLocator(private val source: R8MappingSource) {

  /**
   * Returns a map of package names (or empty string) to mapping file paths.
   *
   * For each package, prefers the selected build variant's mapping file, falling back to the most recently modified file on disk.
   */
  fun getMappings(): Map<String, String> {
    val existingMappings = source.getMappings().filter { Files.exists(it.text) }
    val byPackage = existingMappings.groupBy { it.applicationId ?: "" }

    val result = mutableMapOf<String, String>()
    for ((pkg, mappings) in byPackage) {
      val chosen =
        mappings.filter { it.isSelected }.maxByOrNull { getFileModifiedTime(it.text) }
          ?: mappings.maxByOrNull { getFileModifiedTime(it.text) }

      if (chosen != null) {
        result[pkg] = chosen.text.toAbsolutePath().toString()
      }
    }
    return result
  }

  private fun getFileModifiedTime(path: Path): Long {
    return try {
      Files.getLastModifiedTime(path).toMillis()
    } catch (e: Exception) {
      0L
    }
  }
}
