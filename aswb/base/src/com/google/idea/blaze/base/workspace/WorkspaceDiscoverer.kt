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

import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.getWorkspaceTarget
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Metadata for a discovered alternative workspace.
 *
 * @property path The absolute Path to the physical workspace root.
 * @property name The user-friendly logical name of the workspace displayed in chooser lists.
 */
data class WorkspaceDescriptor(val path: Path, val name: String)

/** Extension point interface for discovering alternative/sibling physical workspaces. */
interface WorkspaceDiscoverer {
  companion object {
    @JvmField val EP_NAME = ExtensionPointName.create<WorkspaceDiscoverer>("com.google.idea.blaze.WorkspaceDiscoverer")

    /** Resolves and returns the applicable discoverer for the project. */
    @JvmStatic
    fun getWorkspaceDiscoverer(project: Project): WorkspaceDiscoverer {
      val all = runCatching { EP_NAME.extensions.toList() }.getOrElse { emptyList() }
      return all.asReversed().firstOrNull { it.isApplicable(project) } ?: DefaultWorkspaceDiscoverer()
    }
  }

  /** Returns whether this discoverer is applicable to the given project. */
  fun isApplicable(project: Project): Boolean

  /** Discovers and returns alternative physical workspace descriptors for the project. */
  fun discoverWorkspaces(project: Project): List<WorkspaceDescriptor>

  /**
   * Generates a WorkspaceDescriptor metadata record for the given physical workspace path.
   *
   * @param physicalWorkspacePath the physical directory path on disk
   * @return the compiled WorkspaceDescriptor, or null if invalid
   */
  fun getWorkspaceDescriptor(physicalWorkspacePath: Path): WorkspaceDescriptor? {
    return WorkspaceDescriptor(physicalWorkspacePath, physicalWorkspacePath.fileName?.toString() ?: "")
  }
}

/** Default WorkspaceDiscoverer implementation that scans sibling directories of the active project. */
class DefaultWorkspaceDiscoverer : WorkspaceDiscoverer {
  override fun isApplicable(project: Project): Boolean = true

  override fun discoverWorkspaces(project: Project): List<WorkspaceDescriptor> {
    val physicalWorkspaceRoot = project.getWorkspaceTarget() ?: return emptyList()

    // Discover the shared repository folder housing the physical workspaces (e.g. /src/)
    val parentWorkspaceDir = physicalWorkspaceRoot.parent?.toFile() ?: return emptyList()
    val activeRootStr = physicalWorkspaceRoot.toString()

    // Returns all alternative physical candidate workspace siblings statelessly
    return parentWorkspaceDir
      .listFiles()
      ?.filter { it.isDirectory && it.absolutePath != activeRootStr }
      ?.map { WorkspaceDescriptor(it.toPath(), it.name) } ?: emptyList()
  }
}
