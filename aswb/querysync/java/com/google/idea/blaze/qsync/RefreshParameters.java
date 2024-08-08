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
package com.google.idea.blaze.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Input parameters to a project refresh, and logic to determine what sort of refresh is required.
 */
public class RefreshParameters {

  final PostQuerySyncData currentProject;
  final Optional<VcsState> latestVcsState;
  final Optional<String> latestBazelVersion;
  final ProjectDefinition latestProjectDefinition;
  final VcsStateDiffer vcsDiffer;

  RefreshParameters(
      PostQuerySyncData currentProject,
      Optional<VcsState> latestVcsState,
      Optional<String> latestBazelVersion,
      ProjectDefinition latestProjectDefinition,
      VcsStateDiffer vcsDiffer) {
    this.currentProject = currentProject;
    this.latestVcsState = latestVcsState;
    this.latestBazelVersion = latestBazelVersion;
    this.latestProjectDefinition = latestProjectDefinition;
    this.vcsDiffer = vcsDiffer;
  }

  boolean requiresFullUpdate(Context<?> context) {
    if (!currentProject.querySummary().isCompatibleWithCurrentPluginVersion()) {
      context.output(PrintOutput.output("IDE has updated since last sync; performing full query"));
      return true;
    }
    if (!currentProject.projectDefinition().equals(latestProjectDefinition)) {
      context.output(PrintOutput.output("Project definition has changed; performing full query"));
      return true;
    }
    if (!currentProject.vcsState().isPresent()) {
      context.output(PrintOutput.output("No VCS state from last sync: performing full query"));
      return true;
    }
    if (!latestVcsState.isPresent()) {
      context.output(
          PrintOutput.output("VCS doesn't support delta updates: performing full query"));
      return true;
    }
    if (!Objects.equals(
        currentProject.vcsState().get().workspaceId, latestVcsState.get().workspaceId)) {
      context.output(
          PrintOutput.output(
              "Workspace has changed %s -> %s: performing full query",
              currentProject.vcsState().get().workspaceId, latestVcsState.get().workspaceId));
      return true;
    }
    if (!Objects.equals(
        currentProject.vcsState().get().upstreamRevision, latestVcsState.get().upstreamRevision)) {
      context.output(
          PrintOutput.output(
              "Upstream revision has changed %s -> %s: performing full query",
              currentProject.vcsState().get().upstreamRevision,
              latestVcsState.get().upstreamRevision));
      return true;
    }
    if (!Objects.equals(currentProject.bazelVersion(), latestBazelVersion)) {
      context.output(
          PrintOutput.output(
              "Bazel version has changed %s -> %s",
              currentProject.bazelVersion().orElse(null), latestBazelVersion.orElse(null)));
      return true;
    }
    return false;
  }

  AffectedPackages calculateAffectedPackages(Context<?> context) throws BuildException {
    // Build the effective working set. This includes the working set as was when the original
    // sync query was run, as it's possible that files have been reverted since then but the
    // earlier query output will reflect the un-reverted file state.

    ImmutableSet<Path> newWorkingSetFiles =
        latestVcsState.get().workingSet.stream()
            .map(c -> c.workspaceRelativePath)
            .collect(toImmutableSet());

    // Files that were in the working set previously, but are no longer, must have been reverted.
    // Find them, and then invert them to ensure that all state is updated appropriately.
    ImmutableSet<WorkspaceFileChange> revertedChanges =
        currentProject.vcsState().get().workingSet.stream()
            .filter(c -> !newWorkingSetFiles.contains(c.workspaceRelativePath))
            .map(WorkspaceFileChange::invert)
            .collect(toImmutableSet());

    Set<WorkspaceFileChange> changed = Sets.union(latestVcsState.get().workingSet, revertedChanges);

    if (currentProject.vcsState().isPresent() && latestVcsState.isPresent()) {
      Optional<ImmutableSet<Path>> filesChanged =
          vcsDiffer.getFilesChangedBetween(latestVcsState.get(), currentProject.vcsState().get());
      if (filesChanged.isPresent()) {
        // filter out files that didn't actually change:
        changed =
            changed.stream()
                .filter(c -> filesChanged.get().contains(c.workspaceRelativePath))
                .collect(toImmutableSet());
      }
    }

    return AffectedPackagesCalculator.builder()
        .context(context)
        .projectIncludes(currentProject.projectDefinition().projectIncludes())
        .projectExcludes(currentProject.projectDefinition().projectExcludes())
        .changedFiles(changed)
        .lastQuery(currentProject.querySummary())
        .build()
        .getAffectedPackages();
  }
}
