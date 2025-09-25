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
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.java.JavaArtifactMetadata.AarResPackage
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform
import java.nio.file.Path
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
    return if (projectDefinition.isIncluded(javaInfo.label())) emptyList() else listOfNotNull(javaInfo.ideAar())
  }

  override fun getRequiredArtifacts(
    forTarget: TargetBuildInfo
  ): Map<BuildArtifact, Collection<ArtifactMetadata.Extractor<*>>> {
    return getDependencyAars(forTarget).associateWith { listOf(aarPackageNameMetadata) }
  }

  @Throws(BuildException::class)
  override fun update(
    update: ProjectProtoUpdate,
    buildGraph: BuildGraphData,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    update.artifactDirectory(ArtifactDirectories.DEFAULT) {
      for (target in artifactState.targets()) {
        val aars = getDependencyAars(target)
        if (aars.isEmpty()) continue
        update.module(target.label()) {
          for (aar in aars) {
            val packageName =
              aar.getMetadata(AarResPackage::class.java).getOrNull()?.name
            val added =
                addIfNewer(aar.artifactPath(), aar, target.buildContext(), ArtifactTransform.UNZIP)
            if (added != null) {
                addExternalAndroidLibrary(ProjectProto.ExternalAndroidLibrary(
                  name = aar.artifactPath().toString().replace('/', '_'),
                  location = added,
                  manifestFile = added.resolveChild(Path.of("AndroidManifest.xml")),
                  resFolder = added.resolveChild(Path.of("res")),
                  symbolFile = added.resolveChild(Path.of("R.txt")),
                  packageName = packageName.orEmpty()
                ))
            }
          }
        }
      }
    }
  }
}
