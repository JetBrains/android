/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.settings

import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.exception.BuildException
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** Interface for querying project-level Bazel/Blaze import settings. */
interface BazelImportSettingsManager {

  /** Returns whether the project has valid import settings. */
  fun hasImportSettings(): Boolean

  /** The workspace root path if configured and the project is a Blaze/Bazel project, null otherwise. */
  val workspaceRoot: Path?

  /** The project name if configured and the project is a Blaze/Bazel project, null otherwise. */
  val projectName: String?

  /** The project view file path if configured and the project is a Blaze/Bazel project, null otherwise. */
  val projectViewFilePath: Path?

  /** The build system used by the project, or null if the project is not a Blaze/Bazel project. */
  val buildSystem: BuildSystemName?

  /** The current project view collection, or null if not loaded, not configured, or if there is an error. */
  val projectViewSet: ProjectViewSet?

  /** Reloads the project view, replacing the current one only if there are no errors. */
  @Throws(BuildException::class) fun reloadProjectView(): ProjectViewSet?

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BazelImportSettingsManager {
      return project.getService(BazelImportSettingsManager::class.java)
    }
  }
}
