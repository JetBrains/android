/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.FileExtensions
import java.nio.file.Path

/**
 * Interface for reading the project structure from the file system.
 *
 * Implementations are expected to scan the workspace based on the project definition
 * and return a [ProjectStructureData] object.
 */
interface ProjectStructureReader {
  /**
   * Reads the project structure.
   *
   * @param context The context for logging.
   * @param workspaceRoot The absolute path to the workspace root.
   * @param projectDefinition The project definition containing includes and excludes.
   * @return A [ProjectStructureData] instance representing the project structure.
   */
  fun read(
    context: Context<*>,
    workspaceRoot: Path,
    projectDefinition: ProjectDefinition,
  ): ProjectStructureData

  companion object {
    fun create(fileExtensions: FileExtensions): ProjectStructureReader =
      ProjectStructureReaderImpl(fileExtensions)
  }
}
