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

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.cc.ConfigureCcSources
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.java.WorkspaceResolvingPackageReader
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import java.nio.file.Path

/**
 * Project refresher creates an appropriate [RefreshOperation] based on the project and
 * current VCS state.
 */
class ProjectBuilder(
  private val executor: ListeningExecutorService,
  private val packageReader: PackageReader,
  private val parallelPackageReader: PackageReader.ParallelReader,
  private val workspaceRoot: Path,
) {

  /**
   * Creates a [QuerySyncProjectSnapshot], which includes an expected IDE project structure,
   * from the `postQuerySyncData` and a function `applyBuiltDependenciesTransform` that
   * applies transformations required to account for any currently synced(i.e. built) dependencies.
   */
  @Throws(BuildException::class)
  fun createBlazeProjectStructure(
    context: Context<*>,
    postQuerySyncData: PostQuerySyncData,
    graph: BuildGraphData,
    artifactTrackerState: ArtifactTracker.State,
    projectProtoUpdates: Collection<ProjectProtoUpdateOperation>,
  ): ProjectProto.Project {
    val effectiveWorkspaceRoot =
      postQuerySyncData.vcsState().flatMap { it.workspaceSnapshotPath }.orElse(workspaceRoot)
    val packageReader = WorkspaceResolvingPackageReader(effectiveWorkspaceRoot, this.packageReader)
    val javaPackagePrefixReader: JavaPackagePrefixReader =
      JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader)

    val graphToProjectConverter =
      GraphToProjectConverter(
        javaPackagePrefixReader = javaPackagePrefixReader,
        context = context,
        projectDefinition = postQuerySyncData.projectDefinition()
      )
    val externalRepositoryFinder = ProjectPath.ExternalRepositoryFinder.createAndPrepare(workspaceRoot)

    val update = ProjectProtoUpdate(ProjectProto.Project.getDefaultInstance())
    graphToProjectConverter.createProject(graph, externalRepositoryFinder, update)
    ConfigureCcSources().update(update, graph, context)
    for (updateOperation in projectProtoUpdates) {
      updateOperation.update(update, artifactTrackerState, context, externalRepositoryFinder)
    }
    return update.build()
  }
}
