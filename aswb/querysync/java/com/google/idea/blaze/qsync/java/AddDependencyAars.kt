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

import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSetMultimap
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactDirectoryBuilder
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Function
import kotlin.jvm.optionals.getOrNull

/**
 * Adds external `.aar` files to the project proto as [ExternalAndroidLibrary]s. This
 * allows resources references to external libraries to be resolved in Android Studio.
 */
class AddDependencyAars(
  private val projectDefinition: ProjectDefinition,
  private val aarPackageNameMetadata: ArtifactMetadata.Extractor<AarResPackage>
) : ProjectProtoUpdateOperation {

  private fun getDependencyAars(target: TargetBuildInfo): Collection<BuildArtifact> {
    val javaInfo = target.javaInfo().getOrNull() ?: return emptyList()
    return if (projectDefinition.isIncluded(javaInfo.label())) emptyList() else javaInfo.ideAars()
  }

  override fun getRequiredArtifacts(
    forTarget: TargetBuildInfo
  ): Map<BuildArtifact, Collection<ArtifactMetadata.Extractor<*>>> {
    return getDependencyAars(forTarget).associateWith { listOf(aarPackageNameMetadata) }
  }

  @Throws(BuildException::class)
  override fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    var aarDir: ArtifactDirectoryBuilder? = null
    for (target in artifactState.targets()) {
      for (aar in getDependencyAars(target)) {
        if (aarDir == null) {
          aarDir = update.artifactDirectory(ArtifactDirectories.DEFAULT)
        }
        val packageName =
          aar.getMetadata(AarResPackage::class.java).getOrNull()?.name
        val dest =
          aarDir
            .addIfNewer(aar.artifactPath(), aar, target.buildContext(), ArtifactTransform.UNZIP)
            .orElse(null)
        if (dest != null) {
          val lib =
            ProjectProto.ExternalAndroidLibrary.newBuilder()
              .setName(aar.artifactPath().toString().replace('/', '_'))
              .setLocation(dest.toProto())
              .setManifestFile(dest.resolveChild(Path.of("AndroidManifest.xml")).toProto())
              .setResFolder(dest.resolveChild(Path.of("res")).toProto())
              .setSymbolFile(dest.resolveChild(Path.of("R.txt")).toProto())
          packageName?.let { lib.setPackageName(it) }
          update.workspaceModule().addAndroidExternalLibraries(lib)
        }
      }
    }
  }
}
