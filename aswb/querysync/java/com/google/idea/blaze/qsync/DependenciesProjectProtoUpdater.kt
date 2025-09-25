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
package com.google.idea.blaze.qsync

import com.google.common.base.Supplier
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.cc.ConfigureCcCompilation.UpdateOperation
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.AddCompiledJavaDeps
import com.google.idea.blaze.qsync.java.AddDependencyGenSrcsJars
import com.google.idea.blaze.qsync.java.AddDependencySrcJars
import com.google.idea.blaze.qsync.java.AddProjectGenSrcJars
import com.google.idea.blaze.qsync.java.AddProjectGenSrcs
import com.google.idea.blaze.qsync.java.JavaSourcePackageExtractor
import com.google.idea.blaze.qsync.java.PackageStatementParser
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder
import com.google.idea.blaze.qsync.java.SrcJarPackageRootsExtractor
import com.google.idea.blaze.qsync.java.SrcJarPrefixedPackageRootsExtractor
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.update.ProjectProtoTransform
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation

/**
 * A [ProjectProtoTransform] that adds built artifact information to the project proto, based
 * on all artifacts that have been built.
 */
class DependenciesProjectProtoUpdater(
  projectDefinition: ProjectDefinition,
  pathResolver: ProjectPath.Resolver,
  emptyJarDigests: Set<String>,
  attachDepsSrcjarsExperiment: Supplier<Boolean>
) : ProjectProtoTransform {
  private val updateOperations: List<ProjectProtoUpdateOperation>

  init {
    // Require empty package prefixes for srcjar inner paths, since the ultimate consumer of these
    // paths does not support setting a package prefix (see `Library.ModifiableModel.addRoot`).
    val packageReader = PackageStatementParser()
    val srcJarInnerPathFinder = SrcJarInnerPathFinder(packageReader)
    this.updateOperations =
      listOf(
        AddCompiledJavaDeps(emptyJarDigests),
        AddProjectGenSrcJars(projectDefinition, SrcJarPrefixedPackageRootsExtractor(srcJarInnerPathFinder)),
        AddProjectGenSrcs(projectDefinition, JavaSourcePackageExtractor(packageReader)),
        UpdateOperation(),
      ) +
      if (attachDepsSrcjarsExperiment.get())
        listOf(
          AddDependencySrcJars(
            projectDefinition,
            pathResolver,
            srcJarInnerPathFinder
          ),
          AddDependencyGenSrcsJars(
            projectDefinition, SrcJarPackageRootsExtractor(srcJarInnerPathFinder)
          )
        )
      else emptyList()
  }

  override fun getRequiredArtifactMetadata(forTarget: TargetBuildInfo): Map<BuildArtifact, Set<ArtifactMetadata.Extractor<*>>> {
    return buildMap {
      for (op in updateOperations) {
        for (entry in op.getRequiredArtifacts(forTarget).entries) {
          for (extractor in entry.value) {
            compute(entry.key) { k, v -> v.orEmpty() + extractor }
          }
        }
      }
    }
  }

  @Throws(BuildException::class)
  override fun apply(
    update: ProjectProtoUpdate,
    graph: BuildGraphData,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    for (op in updateOperations) {
      op.update(update, artifactState, context)
    }
  }
}
