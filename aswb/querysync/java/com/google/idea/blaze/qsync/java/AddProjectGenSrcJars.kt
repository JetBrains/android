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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSetMultimap
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.SrcJarPrefixedJavaPackageRoots
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher
import java.util.function.Function
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

/**
 * Adds in-project generated `.srcjar` files to the project proto. This allows these sources
 * to be resolved and viewed.
 */
class AddProjectGenSrcJars(
  private val projectDefinition: ProjectDefinition,
  private val srcJarPathMetadata: ArtifactMetadata.Extractor<SrcJarPrefixedJavaPackageRoots>
) : ProjectProtoUpdateOperation {
  private val testSourceMatcher: TestSourceGlobMatcher = TestSourceGlobMatcher.create(projectDefinition)

  private fun getProjectGenSrcJars(target: TargetBuildInfo): Collection<BuildArtifact> {
    val javaInfo = target.javaInfo().getOrNull() ?: return emptyList()
    if (!projectDefinition.isIncluded(javaInfo.label())) {
      return emptyList()
    }
    return javaInfo.genSrcs().filter { ProjectProtoUpdateOperation.Companion.JAVA_ARCHIVE_EXTENSIONS.contains(it.getExtension()) }
  }

  override fun getRequiredArtifacts(
    forTarget: TargetBuildInfo
  ): Map<BuildArtifact, Collection<ArtifactMetadata.Extractor<*>>> {
    return getProjectGenSrcJars(forTarget).associateWith { listOf(srcJarPathMetadata) }
  }

  override fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    for (target in artifactState.targets()) {
      getProjectGenSrcJars(target)
        .forEach { genSrc ->
          // a zip of generated sources
          val added =
            update
              .artifactDirectory(ArtifactDirectories.JAVA_GEN_SRC)
              .addIfNewer(
                genSrc.artifactPath().resolve("src"),
                genSrc,
                target.buildContext(),
                ArtifactTransform.UNZIP
              )
              .orElse(null)
          if (added != null) {
            val genSrcJarContentEntry =
              ProjectProto.ContentEntry.newBuilder().setRoot(added.toProto())

            val packageRoots =
              genSrc
                .getMetadata(SrcJarPrefixedJavaPackageRoots::class.java)
                .getOrNull()
                ?.paths()
              ?: ImmutableSet.of(JarPath.create("", ""))
            for (innerPath in packageRoots) {
              genSrcJarContentEntry.addSources(
                ProjectProto.SourceFolder.newBuilder()
                  .setProjectPath(added.resolveChild(innerPath.path).toProto())
                  .setIsGenerated(true)
                  .setIsTest(testSourceMatcher.matches(genSrc.target().getBuildPackagePath()))
                  .setPackagePrefix(innerPath.packagePrefix)
                  .build()
              )
            }
            update.workspaceModule().addContentEntries(genSrcJarContentEntry.build())
          }
        }
    }
  }
}
