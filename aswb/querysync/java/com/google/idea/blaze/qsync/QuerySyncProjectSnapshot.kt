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

import com.google.common.base.Preconditions
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.deps.ArtifactIndex
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectProto
import java.nio.file.Path

/**
 * A fully sync'd project at a point in time. This consists of:
 *
 *
 *  * The output from the query part of sync, [.queryData].
 *  * Build graph information derived form the sync data, [.graph].
 *  * The output from all dependency builds to date, [.artifactState].
 *  * The IntelliJ project structure derived from the above, presented as a proto, [       ][.project].
 *
 *
 *
 * This class is immutable, any modifications to the project will yield a new instance.
 */
data class QuerySyncProjectSnapshot(
  val queryData: PostQuerySyncData,
  val graph: BuildGraphData,
  val artifactState: ArtifactTracker.State,
  val project: ProjectProto.Project,
  val incompleteTargets: Set<Label>
){
    companion object {
      @JvmField
      val EMPTY = QuerySyncProjectSnapshot(
        queryData = PostQuerySyncData.EMPTY,
        graph = BuildGraphData.EMPTY,
        artifactState = ArtifactTracker.State.EMPTY,
        project = ProjectProto.Project.getDefaultInstance(),
        incompleteTargets = emptySet()
      )
  }

  fun withQueryData(value: PostQuerySyncData): QuerySyncProjectSnapshot = copy(queryData = value)
  fun withGraph(value: BuildGraphData): QuerySyncProjectSnapshot = copy(graph = value)
  fun withArtifactState(value: ArtifactTracker.State): QuerySyncProjectSnapshot = copy(artifactState = value)
  fun withProject(value: ProjectProto.Project): QuerySyncProjectSnapshot = copy(project = value)

  /**
   * Given a path to a file it returns the targets that own the file.
   *
   * @param path a workspace relative path.
   */
  fun getTargetOwners(path: Path): Set<Label> {
    return graph.getSourceFileOwners(path)
  }

  val allLoadedTargets: Collection<Label>
    /** Returns mapping of targets to [BuildTarget]  */
    get() = graph.allLoadedTargets()

  val artifactIndex: ArtifactIndex by lazy(LazyThreadSafetyMode.PUBLICATION) {ArtifactIndex.create(artifactState) }

  /**
   * For given project targets, returns all dependency targets that are [ ][BuildGraphDataImpl.projectDeps] external} to the project,
   * from which build artifacts are needed for the targets sources to be edited fully. This method returns the dependencies for the target
   * with fewest pending so that if dependencies have been built for one, the empty set will be
   * returned even if others have pending dependencies.
   *
   * @param projectTargets The set of project targets which include a given source file.
   */
  fun getPendingExternalDeps(projectTargets: Set<Label>): Set<Label> {
    val syncedTargets = artifactState?.deprecatedSyncedTargetKeys().orEmpty()
    val incompleteTargets: Set<Label> = incompleteTargets
    return projectTargets
      .map { target ->
        graph
          .computeRequestedTargets(listOf(target), replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false)
          .requiredTargets
          .filter { !syncedTargets.contains(it) || incompleteTargets.contains(it) }
          .toSet()
      }
      .minByOrNull { it.size }
      .orEmpty()
  }

  /** Recursively get all the transitive deps outside the project  */
  fun getPendingTargets(workspaceRelativePath: Path): Set<Label> {
    Preconditions.checkState(!workspaceRelativePath.isAbsolute, workspaceRelativePath)
    return getPendingExternalDeps(getTargetOwners(workspaceRelativePath))
  }
}
