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

import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A project update that does nothing other than return the existing project state, with the vcs
 * state updated.
 *
 * <p>This is used when performing a partial sync when there have been no significant changes to
 * targets in the workspace (e.g. dependencies modified or source files added) since the last sync.
 */
public class NoopProjectRefresh implements RefreshOperation {

  private final Supplier<QuerySyncProjectSnapshot> latestProjectSnapshotSupplier;
  private final Optional<VcsState> currentVcsState;
  private final Optional<String> bazelVersion;

  public NoopProjectRefresh(
      Supplier<QuerySyncProjectSnapshot> latestProjectSnapshotSupplier,
      Optional<VcsState> currentVcsState,
      Optional<String> bazelVersion) {
    this.latestProjectSnapshotSupplier = latestProjectSnapshotSupplier;
    this.currentVcsState = currentVcsState;
    this.bazelVersion = bazelVersion;
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() {
    return Optional.empty();
  }

  @Override
  public PostQuerySyncData createPostQuerySyncData(QuerySummary output) {
    return latestProjectSnapshotSupplier.get().queryData().toBuilder()
        .setVcsState(currentVcsState)
        .setBazelVersion(bazelVersion)
        .build();
  }
}
