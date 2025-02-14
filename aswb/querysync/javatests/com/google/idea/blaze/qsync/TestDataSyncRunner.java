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

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.getQuerySummary;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.java.PackageReader;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.util.Optional;

/**
 * Builds a {@link QuerySyncProjectSnapshot} for a test project by running the logic from the
 * various sync stages on the testdata query output.
 */
public class TestDataSyncRunner {

  private final Context<?> context;
  private final PackageReader packageReader;

  public TestDataSyncRunner(
      Context<?> context, PackageReader packageReader) {
    this.context = context;
    this.packageReader = packageReader;
  }

  public QuerySyncProjectSnapshot sync(TestData testProject) throws IOException, BuildException {
    ProjectDefinition projectDefinition =
        ProjectDefinition.builder()
        .setProjectIncludes(ImmutableSet.copyOf(testProject.getRelativeSourcePaths()))
          .setProjectExcludes(ImmutableSet.of())
          .setSystemExcludes(ImmutableSet.of())
          .setTestSources(ImmutableSet.of())
          .setLanguageClasses(ImmutableSet.of())
        .build();
    QuerySummary querySummary = getQuerySummary(testProject);
    PostQuerySyncData pqsd =
        PostQuerySyncData.builder()
            .setProjectDefinition(projectDefinition)
            .setQuerySummary(querySummary)
            .setVcsState(Optional.empty())
            .setBazelVersion(Optional.empty())
            .build();
    BuildGraphData buildGraphData =
        new BlazeQueryParser(querySummary, context, ImmutableSet.of()).parse();
    GraphToProjectConverter converter =
        new GraphToProjectConverter(
            packageReader,
            Predicates.alwaysTrue(),
            context,
            projectDefinition,
            newDirectExecutorService());
    Project project = converter.createProject(buildGraphData);
    return QuerySyncProjectSnapshot.builder()
        .queryData(pqsd)
        .graph(new BlazeQueryParser(querySummary, context, ImmutableSet.of()).parse())
        .artifactState(ArtifactTracker.State.EMPTY)
        .project(project)
        .build();
  }
}
