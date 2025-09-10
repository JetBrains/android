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
package com.google.idea.blaze.qsync.java

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarJavaPackageRoots
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectProto
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

/**
 * Adds generated `.srcjar` files from external dependencies to the `.dependencies`
 * library. This means that when navigating to these dependencies, we see the generated sources
 * rather than decompiled code.
 */
class AddDependencyGenSrcsJars(
  private val projectDefinition: ProjectDefinition,
  private val srcJarPathsMetadata: ArtifactMetadata.Extractor<SrcJarJavaPackageRoots>
) : ProjectProtoUpdateOperation {
  private fun getDependencyGenSrcJars(target: TargetBuildInfo): Collection<BuildArtifact> {
    val javaInfo = target.javaInfo().getOrNull() ?: return emptyList()

    return if (projectDefinition.isIncluded(javaInfo.label())) emptyList()
    else javaInfo.genSrcs().filter { ProjectProtoUpdateOperation.Companion.JAVA_ARCHIVE_EXTENSIONS.contains(it.getExtension()) }
  }

  override fun getRequiredArtifacts(
    forTarget: TargetBuildInfo,
  ): Map<BuildArtifact, Collection<ArtifactMetadata.Extractor<*>>> {
    return getDependencyGenSrcJars(forTarget).associateWith { listOf(srcJarPathsMetadata) }
  }

  @Throws(BuildException::class)
  override fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    for (target in artifactState.targets()) {
      getDependencyGenSrcJars(target)
        .forEach { genSrc ->
          val projectArtifact =
            update
              .artifactDirectory(ArtifactDirectories.DEFAULT)
              .addIfNewer(genSrc.artifactPath(), genSrc, target.buildContext())
              .orElse(null)
          if (projectArtifact != null) {
            val innerJavaRoots = genSrc
                                   .getMetadata(SrcJarJavaPackageRoots::class.java)
                                   .getOrNull()
                                   ?.roots()
                                 ?: setOf(Path.of(""))
            innerJavaRoots
              .map { projectArtifact.withInnerJarPath(it) }
              .map { it.toProto() }
              .map { ProjectProto.LibrarySource.newBuilder().setSrcjar(it) }
              .forEach {
                update.library(target.label().toString())
                  .addSources(it)
              }
          }
        }
    }
  }
}
