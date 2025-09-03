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
package com.google.idea.blaze.base.qsync

import com.google.idea.blaze.qsync.project.ProjectDirectory
import com.google.idea.blaze.qsync.project.ProjectDirectoryConfigurator
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

class DefaultProjectDirectoryConfigurator(private val project: Project): ProjectDirectoryConfigurator {
  fun resolveDirectory(directory: ProjectDirectory): Path {
    val basePath = Path.of(project.basePath ?: error("Invalid project"))
    return basePath.resolve(directory.directoryName)
  }

  override fun configureDirectory(directory: ProjectDirectory): Path {
    val path = resolveDirectory(directory)
    if (Files.isDirectory(path)) {
      return path
    }
    Files.createDirectory(path)
    return path
  }
}