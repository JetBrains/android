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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.query.QuerySummaryImpl;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Implements a query strategy based on querying a minimal set of packages derived from the VCS
 * working set.
 *
 * <p>Instance of this class will be returned from {@link ProjectRefresher#startPartialRefresh} when
 * appropriate.
 */
class PartialProjectRefresh implements RefreshOperation {

  private final Path workspaceRoot;
  private final PostQuerySyncData previousState;
  private final Optional<VcsState> currentVcsState;
  private final Optional<String> bazelVersion;
  @VisibleForTesting final ImmutableSet<Path> modifiedPackages;
  @VisibleForTesting final ImmutableSet<Path> deletedPackages;

  PartialProjectRefresh(
      Path workspaceRoot,
      PostQuerySyncData previousState,
      Optional<VcsState> currentVcsState,
      Optional<String> bazelVersion,
      ImmutableSet<Path> modifiedPackages,
      ImmutableSet<Path> deletedPackages) {
    this.workspaceRoot = workspaceRoot;
    this.previousState = previousState;
    this.currentVcsState = currentVcsState;
    this.modifiedPackages = modifiedPackages;
    this.deletedPackages = deletedPackages;
    this.bazelVersion = bazelVersion;
  }

  private Optional<QuerySpec> createQuerySpec() {
    if (modifiedPackages.isEmpty()) {
      // this can happen if the user just deletes a build file that doesn't have a parent package.
      return Optional.empty();
    }
    // TODO should we also consider excludes here?
    return Optional.of(
        QuerySpec.builder(this.previousState.querySummary().getQueryStrategy())
            .includePackages(modifiedPackages)
            .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
            .build());
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() {
    return createQuerySpec();
  }

  @Override
  public PostQuerySyncData createPostQuerySyncData(QuerySummary partialQuery) {
    Preconditions.checkNotNull(partialQuery, "queryOutput");
    QuerySummary effectiveQuery = applyDelta(partialQuery);
    return PostQuerySyncData.builder()
        .setVcsState(currentVcsState)
        .setBazelVersion(bazelVersion)
        .setProjectDefinition(previousState.projectDefinition())
        .setQuerySummary(effectiveQuery)
        .build();
  }

  /**
   * Calculates the effective query output, based on an earlier full query output, the output from a
   * partial query, and any deleted packages.
   */
  @VisibleForTesting
  QuerySummary applyDelta(QuerySummary partialQuery) {
    List<QuerySummary.BuildPackage> mergedPackages = Lists.newArrayList();

    // 1. Keep previous build packages if unaffected/not deleted:
    for (QuerySummary.BuildPackage pkg : previousState.querySummary().getBuildPackages()) {
      Path buildPackagePath = pkg.getPackageLabel().getBuildPackagePath();
      if (!(deletedPackages.contains(buildPackagePath)
          || partialQuery.getPackages().contains(buildPackagePath))) {
        mergedPackages.add(pkg);
      }
    }

    // 2. Add all new/modified build packages from the delta query:
    mergedPackages.addAll(partialQuery.getBuildPackages());

    // 3. Build the merged summary package-by-package, preserving queryStrategy:
    return QuerySummaryImpl.newBuilder()
        .putAllPackages(mergedPackages)
        .setQueryStrategy(previousState.querySummary().getQueryStrategy())
        .build();
  }
}
