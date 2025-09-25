/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.qsync

import com.google.idea.blaze.android.manifest.ManifestParser
import com.google.idea.blaze.base.qsync.ProjectProtoTransformProvider
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.project.update.ProjectProtoTransform
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.AarPackageNameExtractor
import com.google.idea.blaze.qsync.java.AddAndroidResPackages
import com.google.idea.blaze.qsync.java.AddDependencyAars
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectProto

/** A [ProjectProtoTransform] that adds android specific information to the project proto.  */
class AndroidProjectProtoTransform private constructor(projectDefinition: ProjectDefinition) :
  ProjectProtoTransform {
  override fun getRequiredArtifactMetadata(
    forTarget: TargetBuildInfo
  ): Map<BuildArtifact, Set<ArtifactMetadata.Extractor<*>>> {
    return emptyMap()
  }

  /**
   * Provides a [ProjectProtoTransform] that adds android specific information to the project
   * proto.
   */
  class Provider : ProjectProtoTransformProvider {
    override fun createTransforms(projectDef: ProjectDefinition): List<ProjectProtoTransform> {
      return listOf(AndroidProjectProtoTransform(projectDef))
    }
  }

  private val updateOperations: List<ProjectProtoUpdateOperation> =
    listOf(
      AddDependencyAars(
        projectDefinition,
        AarPackageNameExtractor { ManifestParser.parseManifestFromInputStream(it)?.packageName ?: throw BuildException("Failed to parse manifest") }
      ),
      AddAndroidResPackages()
    )

  @Throws(BuildException::class)
  override fun apply(
    update: ProjectProtoUpdate,
    graph: BuildGraphData,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    for (op in updateOperations) {
      op.update(update, graph, artifactState, context)
    }
  }
}
