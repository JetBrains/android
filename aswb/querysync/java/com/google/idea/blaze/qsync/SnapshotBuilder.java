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

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.java.WorkspaceResolvingPackageReader;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Project refresher creates an appropriate {@link RefreshOperation} based on the project and
 * current VCS state.
 */
public class SnapshotBuilder {

  private final ListeningExecutorService executor;
  private final PackageReader workspaceRelativePackageReader;
  private final Path workspaceRoot;
  private final ImmutableSet<String> handledRuleKinds;
  private final Supplier<Boolean> useNewResDirLogic;
  private final Supplier<Boolean> guessAndroidResPackages;

  public SnapshotBuilder(
      ListeningExecutorService executor,
      PackageReader workspaceRelativePackageReader,
      Path workspaceRoot,
      ImmutableSet<String> handledRuleKinds,
      Supplier<Boolean> useNewResDirLogic,
      Supplier<Boolean> guessAndroidResPackages) {
    this.executor = executor;
    this.workspaceRelativePackageReader = workspaceRelativePackageReader;
    this.workspaceRoot = workspaceRoot;
    this.handledRuleKinds = handledRuleKinds;
    this.useNewResDirLogic = useNewResDirLogic;
    this.guessAndroidResPackages = guessAndroidResPackages;
  }

  /**
   * Creates a {@link QuerySyncProjectSnapshot}, which includes an expected IDE project structure,
   * from the {@code postQuerySyncData} and a function {@code applyBuiltDependenciesTransform} that
   * applies transformations required to account for any currently synced(i.e. built) dependencies.
   */
  public QuerySyncProjectSnapshot createBlazeProjectSnapshot(
      Context<?> context,
      PostQuerySyncData postQuerySyncData,
      ArtifactTracker.State artifactTrackerState,
      ProjectProtoTransform projectProtoTransform)
      throws BuildException {
    Path effectiveWorkspaceRoot =
        postQuerySyncData.vcsState().flatMap(s -> s.workspaceSnapshotPath).orElse(workspaceRoot);
    WorkspaceResolvingPackageReader packageReader =
        new WorkspaceResolvingPackageReader(effectiveWorkspaceRoot, workspaceRelativePackageReader);
    GraphToProjectConverter graphToProjectConverter =
        new GraphToProjectConverter(
            packageReader,
            effectiveWorkspaceRoot,
            context,
            postQuerySyncData.projectDefinition(),
            executor,
            useNewResDirLogic,
            guessAndroidResPackages);
    QuerySummary querySummary = postQuerySyncData.querySummary();
    BuildGraphData graph = new BlazeQueryParser(querySummary, context, handledRuleKinds).parse();
    Project project =
        projectProtoTransform.apply(
            graphToProjectConverter.createProject(graph), graph, artifactTrackerState, context);
    return QuerySyncProjectSnapshot.builder()
        .queryData(postQuerySyncData)
        .graph(graph)
        .artifactState(artifactTrackerState)
        .project(project)
        .build();
  }
}
