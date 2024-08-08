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

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Project refresher creates an appropriate {@link RefreshOperation} based on the project and
 * current VCS state.
 */
public class ProjectRefresher {

  private final VcsStateDiffer vcsDiffer;
  private final Path workspaceRoot;
  private final Supplier<Optional<QuerySyncProjectSnapshot>> latestProjectSnapshotSupplier;

  public ProjectRefresher(
      VcsStateDiffer vcsDiffer,
      Path workspaceRoot,
      Supplier<Optional<QuerySyncProjectSnapshot>> latestProjectSnapshotSupplier) {
    this.vcsDiffer = vcsDiffer;
    this.workspaceRoot = workspaceRoot;
    this.latestProjectSnapshotSupplier = latestProjectSnapshotSupplier;
  }

  public RefreshOperation startFullUpdate(
      Context<?> context,
      ProjectDefinition spec,
      Optional<VcsState> vcsState,
      Optional<String> bazelVersion) {
    Path effectiveWorkspaceRoot =
        vcsState.flatMap(s -> s.workspaceSnapshotPath).orElse(workspaceRoot);
    return new FullProjectUpdate(context, effectiveWorkspaceRoot, spec, vcsState, bazelVersion);
  }

  public RefreshOperation startPartialRefresh(
      Context<?> context,
      PostQuerySyncData currentProject,
      Optional<VcsState> latestVcsState,
      Optional<String> latestBazelVersion,
      ProjectDefinition latestProjectDefinition)
      throws BuildException {
    return startPartialRefresh(
        new RefreshParameters(
            currentProject, latestVcsState, latestBazelVersion, latestProjectDefinition, vcsDiffer),
        context);
  }

  public RefreshOperation startPartialRefresh(RefreshParameters params, Context<?> context)
      throws BuildException {
    if (params.requiresFullUpdate(context)) {
      return startFullUpdate(
          context,
          params.latestProjectDefinition,
          params.latestVcsState,
          params.latestBazelVersion);
    }
    AffectedPackages affected = params.calculateAffectedPackages(context);

    if (affected.isEmpty()) {
      // No consequential changes since last sync
      if (latestProjectSnapshotSupplier.get().isPresent()) {
        // We have full project state. We don't need to do anything.
        context.output(PrintOutput.log("Nothing has changed since last sync."));
        return new NoopProjectRefresh(
            latestProjectSnapshotSupplier.get()::get,
            params.latestVcsState,
            params.latestBazelVersion);
      }
      // else we need to recalculate the project structure. This happens on the first sync after
      // reloading the project.
    }
    // TODO(mathewi) check affected.isIncomplete() and offer (or just do?) a full sync in that case.

    Path effectiveWorkspaceRoot =
        params.latestVcsState.flatMap(s -> s.workspaceSnapshotPath).orElse(workspaceRoot);
    return new PartialProjectRefresh(
        effectiveWorkspaceRoot,
        params.currentProject,
        params.latestVcsState,
        params.latestBazelVersion,
        affected.getModifiedPackages(),
        affected.getDeletedPackages());
  }
}
