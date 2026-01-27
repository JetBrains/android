/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.run.classes

import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.run.classes.BazelClassFileFinder
import com.google.idea.blaze.common.Label
import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.run.RuntimeArtifactCache
import com.google.idea.blaze.base.run.RuntimeArtifactKind
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.qsync.deps.OutputInfo
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class BuildOutcome(
  val status: ProjectSystemBuildManager.BuildStatus,
  val timestamp: Instant,
  val bootClasspath: List<Path> = emptyList(),
  val classFileFinder: ClassFileFinder? = null,
  val externalJars: Collection<Path> = emptyList(),
)

private data class CachedArtifacts(val jars: Collection<Path>, val externalJars: Collection<Path>)

class BuildOutcomeCache {
  private val cache: MutableMap<Label, BuildOutcome> = ConcurrentHashMap()

  fun get(label: Label): BuildOutcome? = cache[label]

  fun getMaxStatus(labels: Collection<Label>): ProjectSystemBuildManager.BuildStatus {
    return labels
             .mapNotNull { cache[it] }
             .maxByOrNull { it.timestamp }
             ?.status
           ?: ProjectSystemBuildManager.BuildStatus.UNKNOWN
  }

  fun cacheOutput(
    project: Project,
    label: Label,
    output: OutputInfo,
    context: BlazeContext,
  ) {
    val outcome =
      if (BuildResult.fromExitCode(output.exitCode).status != BuildResult.Status.SUCCESS) {
        BuildOutcome(ProjectSystemBuildManager.BuildStatus.FAILED, Instant.now())
      }
      else {
        val cache = RuntimeArtifactCache.getInstance(project)
        val jars =
          cache.fetchArtifacts(
            label,
            output.transitiveRuntimeJars,
            context,
            RuntimeArtifactKind.TRANSITIVE_RUNTIME_JAR
          )

        val externalJars = cache.fetchArtifacts(
          label,
          output.externalTransitiveRuntimeJars,
          context,
          RuntimeArtifactKind.EXTERNAL_TRANSITIVE_RUNTIME_JAR
        )

        val artifacts = CachedArtifacts(jars, externalJars)
        BuildOutcome(
          ProjectSystemBuildManager.BuildStatus.SUCCESS,
          Instant.now(),
          bootClasspath = emptyList(),
          BazelClassFileFinder(artifacts.jars),
          artifacts.externalJars
        )
      }
    put(label, outcome)
  }

  fun invalidate(label: Label, status: ProjectSystemBuildManager.BuildStatus) {
    put(label, BuildOutcome(status, Instant.now()))
  }

  private fun put(label: Label, outcome: BuildOutcome) {
    cache[label] = outcome
  }
}
