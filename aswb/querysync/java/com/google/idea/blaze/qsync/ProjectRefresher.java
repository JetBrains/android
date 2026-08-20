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
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.project.ProjectStructureData;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Project refresher creates an appropriate {@link RefreshOperation} based on the project and
 * stamp from {@link ProjectStructureData} and {@link QuerySummary}.
 */
public class ProjectRefresher {

  private final Path workspaceRoot;
  private final QuerySpec.QueryStrategy queryStrategy;
  private final Supplier<Optional<QuerySyncProjectSnapshot>> latestProjectSnapshotSupplier;

  public ProjectRefresher(
      Path workspaceRoot,
      QuerySpec.QueryStrategy queryStrategy,
      Supplier<Optional<QuerySyncProjectSnapshot>> latestProjectSnapshotSupplier) {
    this.workspaceRoot = workspaceRoot;
    this.queryStrategy = queryStrategy;
    this.latestProjectSnapshotSupplier = latestProjectSnapshotSupplier;
  }

  public RefreshOperation startFullUpdate(
      Context<?> context, ProjectDefinition spec, ProjectStructureData projectStructureData) {
    return new FullProjectUpdate(context, workspaceRoot, spec, queryStrategy, projectStructureData);
  }

  public RefreshOperation startPartialRefresh(RefreshParameters params, Context<?> context) {
    if (params.requireFullSync.invoke(context)) {
      return startFullUpdate(context, params.projectDefinition, params.projectStructureData);
    }
    AffectedPackages affected = params.calculateAffectedPackages();

    if (affected.isEmpty()) {
      // No consequential changes since last sync
      if (latestProjectSnapshotSupplier.get().isPresent()) {
        // We have full project state. We don't need to do anything.
        context.output(PrintOutput.log("Nothing has changed since last sync."));
        return new NoopProjectRefresh(latestProjectSnapshotSupplier.get()::get);
      }
      // else we need to recalculate the project structure. This happens on the first sync
      // after reloading the project.
    }
    // TODO(mathewi) check affected.isIncomplete() and offer (or just do?) a full sync in that case.

    return new PartialProjectRefresh(
        workspaceRoot,
        params.lastQuery,
        affected.getModifiedPackages(),
        affected.getDeletedPackages(),
        params.projectStructureData);
  }
}
