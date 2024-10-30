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
import com.google.common.collect.Maps;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.query.Query.SourceFile;
import com.google.idea.blaze.qsync.query.QueryData;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Implements a query strategy based on querying a minimal set of packages derived from the VCS
 * working set.
 *
 * <p>Instance of this class will be returned from {@link ProjectRefresher#startPartialRefresh} when
 * appropriate.
 */
class PartialProjectRefresh implements RefreshOperation {

  private final Path effectiveWorkspaceRoot;
  private final PostQuerySyncData previousState;
  private final Optional<VcsState> currentVcsState;
  private final Optional<String> bazelVersion;
  @VisibleForTesting final ImmutableSet<Path> modifiedPackages;
  @VisibleForTesting final ImmutableSet<Path> deletedPackages;

  PartialProjectRefresh(
      Path effectiveWorkspaceRoot,
      PostQuerySyncData previousState,
      Optional<VcsState> currentVcsState,
      Optional<String> bazelVersion,
      ImmutableSet<Path> modifiedPackages,
      ImmutableSet<Path> deletedPackages) {
    this.effectiveWorkspaceRoot = effectiveWorkspaceRoot;
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
            .workspaceRoot(effectiveWorkspaceRoot)
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
    // copy all unaffected rules / source files to result:
    Map<Label, SourceFile> newSourceFiles = Maps.newHashMap();
    for (Map.Entry<Label, SourceFile> sfEntry :
        previousState.querySummary().getSourceFilesMap().entrySet()) {
      Path buildPackage = sfEntry.getKey().getPackage();
      if (!(deletedPackages.contains(buildPackage)
          || partialQuery.getPackages().contains(buildPackage))) {
        newSourceFiles.put(sfEntry.getKey(), sfEntry.getValue());
      }
    }
    Map<Label, QueryData.Rule> newRules = Maps.newHashMap();
    for (Map.Entry<Label, QueryData.Rule> ruleEntry :
        previousState.querySummary().getRulesMap().entrySet()) {
      Path buildPackage = ruleEntry.getKey().getPackage();
      if (!(deletedPackages.contains(buildPackage)
          || partialQuery.getPackages().contains(buildPackage))) {
        newRules.put(ruleEntry.getKey(), ruleEntry.getValue());
      }
    }
    // now add all rules / source files from the delta
    newSourceFiles.putAll(partialQuery.getSourceFilesMap());
    newRules.putAll(partialQuery.getRulesMap());
    return QuerySummary.newBuilder()
        .putAllSourceFiles(newSourceFiles)
        .putAllRules(newRules)
        .putAllPackagesWithErrors(partialQuery.getPackagesWithErrors())
        .build();
  }
}
