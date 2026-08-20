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
package com.google.idea.blaze.qsync

import com.google.common.collect.Sets
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.common.vcs.WorkspaceFileChange
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import java.util.Optional

/**
 * Input parameters to a project refresh, and logic to determine what sort of refresh is required.
 */
class RefreshParameters(
  @JvmField val currentProject: PostQuerySyncData,
  @JvmField val currentProjectDefinition: ProjectDefinition,
  @JvmField val snapshotVcsState: Optional<VcsState>,
  @JvmField val latestVcsState: Optional<VcsState>,
  @JvmField val snapshotBazelVersion: Optional<String>,
  @JvmField val latestBazelVersion: Optional<String>,
  @JvmField val latestProjectDefinition: ProjectDefinition,
) {

  fun requiresFullUpdate(context: Context<*>): Boolean {
    if (!currentProject.querySummary().isCompatibleWithCurrentPluginVersion) {
      context.output(PrintOutput.output("IDE has updated since last sync; performing full query"))
      return true
    }
    if (currentProjectDefinition != latestProjectDefinition) {
      context.output(PrintOutput.output("Project definition has changed; performing full query"))
      return true
    }
    if (!snapshotVcsState.isPresent) {
      context.output(PrintOutput.output("No VCS state from last sync: performing full query"))
      return true
    }
    if (!latestVcsState.isPresent) {
      context.output(PrintOutput.output("VCS doesn't support delta updates: performing full query"))
      return true
    }
    if (snapshotVcsState.get().workspaceId != latestVcsState.get().workspaceId) {
      context.output(
        PrintOutput.output(
          "Workspace has changed %s -> %s: performing full query",
          snapshotVcsState.get().workspaceId,
          latestVcsState.get().workspaceId,
        )
      )
      return true
    }
    if (snapshotVcsState.get().upstreamRevision != latestVcsState.get().upstreamRevision) {
      context.output(
        PrintOutput.output(
          "Upstream revision has changed %s -> %s: performing full query",
          snapshotVcsState.get().upstreamRevision,
          latestVcsState.get().upstreamRevision,
        )
      )
      return true
    }
    if (snapshotBazelVersion != latestBazelVersion) {
      context.output(
        PrintOutput.output(
          "Bazel version has changed %s -> %s",
          snapshotBazelVersion.orElse(null),
          latestBazelVersion.orElse(null),
        )
      )
      return true
    }
    return false
  }

  @Throws(BuildException::class)
  fun calculateAffectedPackages(context: Context<*>, vcsDiffer: VcsStateDiffer): AffectedPackages {
    val newWorkingSetFiles =
      latestVcsState.get().workingSet.map { it.workspaceRelativePath }.toSet()

    val revertedChanges =
      snapshotVcsState.get().workingSet
        .filter { !newWorkingSetFiles.contains(it.workspaceRelativePath) }
        .map { it.invert() }
        .toSet()

    var changed: Set<WorkspaceFileChange> = latestVcsState.get().workingSet + revertedChanges

    if (snapshotVcsState.isPresent && latestVcsState.isPresent) {
      val filesChanged = vcsDiffer.getFilesChangedBetween(latestVcsState.get(), snapshotVcsState.get())
      if (filesChanged.isPresent) {
        changed = changed.filter { filesChanged.get().contains(it.workspaceRelativePath) }.toSet()
      }
    }

    return AffectedPackagesCalculator.builder()
      .context(context)
      .projectScope { currentProjectDefinition.isIncluded(it) }
      .changedFiles(changed)
      .lastQuery(currentProject.querySummary())
      .build()
      .getAffectedPackages()
  }
}
