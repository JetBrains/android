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

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.testdata.TestData
import java.io.IOException
import java.util.Optional

/**
 * Builds a [QuerySyncProjectSnapshot] for a test project by running the logic from the
 * various sync stages on the testdata query output.
 */
class TestDataSyncRunner(
  private val context: Context<*>,
  private val javaPackagePrefixReader: JavaPackagePrefixReader
) {
  @Throws(IOException::class, BuildException::class)
  fun sync(testProject: TestData): QuerySyncProjectSnapshot {
    val projectDefinition =
      ProjectDefinition(
        projectIncludes = testProject.getRelativeSourcePaths().toSet(),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        systemExcludes = emptySet(),
        testSources = emptySet(),
        isAndroidWorkspace = true,
        languageClasses = emptySet(),
      )
    val querySummary = QuerySyncTestUtils.getQuerySummary(testProject)
    val pqsd =
      PostQuerySyncData.builder()
        .setProjectDefinition(projectDefinition)
        .setQuerySummary(querySummary)
        .setVcsState(Optional.empty())
        .setBazelVersion(Optional.empty())
        .build()
    val buildGraphData =
      BlazeQueryParser(querySummary, context, ImmutableSet.of()).parse()
    val converter =
      GraphToProjectConverter(
        javaPackagePrefixReader = javaPackagePrefixReader,
        context = context,
        projectDefinition = projectDefinition
      )
    val update = ProjectProtoUpdate(existingProject = ProjectProto.Project.getDefaultInstance())
    converter.createProject(buildGraphData, ProjectPath.ExternalRepositoryFinder.createEmptyForTests(), update)
    val project = update.build()
    return QuerySyncProjectSnapshot(
      queryData = pqsd,
      graph = BlazeQueryParser(querySummary, context, ImmutableSet.of()).parse(),
      artifactState = ArtifactTracker.State.EMPTY,
      project = project,
      incompleteTargets = emptySet()
    )
  }
}
