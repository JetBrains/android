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

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.cc.ConfigureCcSources
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import java.nio.file.Path

/**
 * Responsible for constructing the [ProjectProto.Project] for the IDE.
 *
 * It orchestrates the reading of the project structure (either from the build graph or via directory traversal) and conversion to the proto
 * format.
 */
class ProjectBuilder(private val workspaceRoot: Path) {
  /**
   * Creates a [ProjectProto.Project] for the IDE.
   *
   * This function orchestrates the conversion of build graph data and project structure data into a [ProjectProto.Project]. It also applies
   * various updates and configurations, including CC source configuration and other project proto updates.
   */
  @Throws(BuildException::class)
  fun createBlazeProjectStructure(
    context: Context<*>,
    projectDefinition: ProjectDefinition,
    graph: BuildGraphData,
    projectStructureData: ProjectStructureData,
    artifactTrackerState: ArtifactTracker.State,
    projectProtoUpdates: Collection<ProjectProtoUpdateOperation>,
  ): ProjectProto.Project {
    val graphToProjectConverter = GraphToProjectConverter(context = context, projectDefinition = projectDefinition)
    val externalRepositoryFinder = ProjectPath.ExternalRepositoryFinder.createAndPrepare(workspaceRoot)

    val update = ProjectProtoUpdate(ProjectProto.Project.getDefaultInstance())
    graphToProjectConverter.configureProject(projectStructureData, externalRepositoryFinder, update)
    graphToProjectConverter.configureProject(graph, externalRepositoryFinder, update)
    ConfigureCcSources().update(update, graph, context)
    for (updateOperation in projectProtoUpdates) {
      updateOperation.update(update, artifactTrackerState, context, externalRepositoryFinder)
    }
    return update.build()
  }
}
