/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.google.idea.blaze.qsync.project.getAllAmbiguous
import com.google.idea.blaze.qsync.project.getAllUnambiguous
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/** Utility for identifying ambiguities in targets to build for files  */
data class TargetDisambiguator(val unambiguousTargets: Set<Label>, val ambiguousTargetSets: Set<TargetsToBuild>) {
  constructor(targetsToPath: Set<TargetsToBuild>): this(
    targetsToPath.getAllUnambiguous(),
    targetsToPath.getAllAmbiguous(),
  )

  /**
   * Finds the sets of targets that cannot be unambiguously resolved.
   *
   *
   * The is the set of ambiguous targets sets which contain no targets that overlap with the
   * unambiguous set of targets.
   */
  fun calculateUnresolvableTargets(): Set<TargetsToBuild> {
    return ambiguousTargetSets.filter { (it.targets intersect unambiguousTargets).isEmpty() }.toSet()
  }

  companion object {
    @JvmStatic
    @JvmName("createForFiles")
    fun BuildDependenciesHelper.createDisambiguatorForFiles(project: Project, files: Set<VirtualFile>): TargetDisambiguator {
      val workspaceRoot = WorkspaceRoot.fromProject(project).path()
      return createDisambiguatorForPaths(
        files
          .mapNotNull{ it.fileSystem.getNioPath(it) }
          .filter { it.startsWith(workspaceRoot) }
          .map { workspaceRoot.relativize(it) }
          .toSet())
    }

    @JvmStatic
    @JvmName("createForPaths")
    fun BuildDependenciesHelper.createDisambiguatorForPaths(workspaceRelativePaths: Set<Path>): TargetDisambiguator {
      // Find the targets to build per source file, and de-dupe then such that if several source files
      // are built by the same set of targets, we consider them as one. Map these results back to an
      // original source file to so we can show it in the UI:
      return TargetDisambiguator(workspaceRelativePaths.map { getTargetsToEnableAnalysisFor(it) }.toSet())
    }
  }
}
