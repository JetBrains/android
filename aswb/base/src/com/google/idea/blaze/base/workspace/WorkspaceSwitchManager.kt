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
package com.google.idea.blaze.base.workspace

import com.google.idea.common.experiments.BoolExperiment
import com.google.idea.switcher.WorkspaceMappingManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlinx.coroutines.flow.SharedFlow

/** Extension methods and helpers for configuring and retrieving active workspace redirection. */
object WorkspaceSwitchManager {
  @JvmField val WORKSPACE_SWITCHER_ENABLED = BoolExperiment("aswb.workspace.switcher.enabled", false)

  val mappingChangeEvents: SharedFlow<Unit>
    get() = WorkspaceMappingManager.getInstance().mappingChangeEvents

  /** Resolves the currently active physical workspace target directory path. */
  @JvmStatic
  fun Project.getWorkspaceTarget(): Path? {
    return WorkspaceMappingManager.getInstance().getWorkspaceTarget(locationHash)
  }

  /** Resolves the currently active physical workspace target directory path. */
  @JvmStatic
  fun Project.getWorkspacePath(): Path {
    return WorkspaceMappingManager.getInstance().getWorkspacePath(locationHash)
  }

  /**
   * Redirects the active physical workspace target natively on disk.
   *
   * @return the absolute Path representing the virtual switcher on disk.
   */
  @JvmStatic
  fun Project.setWorkspaceTarget(physicalTarget: Path): Path {
    return WorkspaceMappingManager.getInstance().setWorkspaceTarget(locationHash, physicalTarget).also {
      this.service<WorkspaceSwitcherStateService>().publishUpdate()
    }
  }
}
