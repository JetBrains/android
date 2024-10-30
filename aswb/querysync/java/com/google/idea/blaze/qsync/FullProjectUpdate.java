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
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A project update based on a query of all targets in the project.
 *
 * <p>This strategy is used when creating a new project from scratch, or when updating the project
 * if a partial query cannot be used.
 */
public class FullProjectUpdate implements RefreshOperation {

  private final Context<?> context;
  private final Path effectiveWorkspaceRoot;
  private final ProjectDefinition projectDefinition;
  private final Optional<VcsState> vcsState;
  private final Optional<String> bazelVersion;
  private final QuerySpec.QueryStrategy queryStrategy;

  public FullProjectUpdate(
    Context<?> context,
    Path effectiveWorkspaceRoot,
    ProjectDefinition definition,
    Optional<VcsState> vcsState,
    Optional<String> bazelVersion,
    QuerySpec.QueryStrategy queryStrategy) {
    this.context = context;
    this.effectiveWorkspaceRoot = effectiveWorkspaceRoot;
    this.projectDefinition = definition;
    this.vcsState = vcsState;
    this.bazelVersion = bazelVersion;
    this.queryStrategy = queryStrategy;
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() throws IOException {
    return Optional.of(
      projectDefinition
        .deriveQuerySpec(context, queryStrategy, effectiveWorkspaceRoot)
        .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
        .build());
  }

  @Override
  public PostQuerySyncData createPostQuerySyncData(QuerySummary output) {
    return PostQuerySyncData.builder()
      .setProjectDefinition(projectDefinition)
      .setVcsState(vcsState)
      .setBazelVersion(bazelVersion)
      .setQuerySummary(output)
      .build();
  }
}
