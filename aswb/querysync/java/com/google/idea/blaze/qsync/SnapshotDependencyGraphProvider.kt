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
package com.google.idea.blaze.qsync

import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.OverrideDependencyGraphProvider
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.DependencyGraphProvider
import com.google.idea.blaze.qsync.project.ProjectTarget
import com.google.idea.blaze.qsync.project.Target
import com.google.idea.blaze.qsync.project.TargetStatus
import org.jetbrains.annotations.TestOnly

/**
 * Exposes the [QuerySyncProjectSnapshot]'s build graph as a [DependencyGraphProvider].
 *
 * This provider dynamically resolves Blaze [Label]s into [Target] instances, evaluating dependencies and their project scopes (e.g.,
 * whether they are built as external dependencies or belong to the project). It is primarily used to traverse the dependency graph for code
 * analysis purposes.
 */
fun QuerySyncProjectSnapshot.getCodeAnalysisDependencyGraphProvider(): DependencyGraphProvider {
  val baseProvider =
    object : DependencyGraphProvider {
      override fun getTarget(label: Label): Target {
        val projectTarget = staleGraph.getProjectTarget(label)
        return if (projectTarget != null) {
          ProjectTargetWrapper(projectTarget, staleGraph, this)
        } else {
          UnknownTargetWrapper(label)
        }
      }
    }
  return ArtifactStateDependencyGraphProvider(artifactState, baseProvider)
}

/** A [DependencyGraphProvider] implementation for testing purposes, operating directly on [BuildGraphData]. */
@TestOnly
fun BuildGraphData.getCodeAnalysisDependencyGraphProvider(): DependencyGraphProvider {
  return object : DependencyGraphProvider {
    override fun getTarget(label: Label): Target {
      val projectTarget = getProjectTarget(label)
      return if (projectTarget != null) {
        ProjectTargetWrapper(projectTarget, this@getCodeAnalysisDependencyGraphProvider, this)
      } else {
        UnknownTargetWrapper(label)
      }
    }
  }
}

/**
 * A wrapper around [ProjectTarget] that implements the [Target] interface. It resolves dependencies on demand using the provided
 * [DependencyGraphProvider].
 */
private class ProjectTargetWrapper(val target: ProjectTarget, val graph: BuildGraphData, val provider: DependencyGraphProvider) : Target {
  override val label: Label = target.label()

  override val dependencies: Collection<Target>
    get() {
      val followDeps =
        target.kind() == "alias" ||
          target.languages().asSequence().map { it.dependencyTrackingBehavior }.any { it.shouldIncludeExternalDependencies }
      return if (followDeps) target.deps().map { provider.getTarget(it) } else emptyList()
    }

  override val status: TargetStatus
    get() = if (graph.isAlwaysBuild(label)) TargetStatus.EXTERNAL_TO_PROJECT_SCOPE else TargetStatus.IN_PROJECT_SCOPE

  override fun equals(other: Any?): Boolean = (other as? Target)?.label == label

  override fun hashCode(): Int = label.hashCode()
}

/** A wrapper for targets not found in the project map, treated as unknown. */
private class UnknownTargetWrapper(override val label: Label) : Target {
  override val dependencies: Collection<Target> = emptyList()

  override val status: TargetStatus
    get() = TargetStatus.UNKNOWN

  override fun equals(other: Any?): Boolean = (other as? Target)?.label == label

  override fun hashCode(): Int = label.hashCode()
}

fun ArtifactStateDependencyGraphProvider(state: ArtifactTracker.State, delegate: DependencyGraphProvider): DependencyGraphProvider {
  return OverrideDependencyGraphProvider(delegate) { label, provider ->
    val buildInfo = state.getTargetBuildInfo(label)
    if (buildInfo is TargetBuildInfo.Java) {
      object : Target {
        override val label: Label = label
        override val status: TargetStatus =
          if (buildInfo.javaInfo.isExternalDependency) {
            TargetStatus.EXTERNAL_TO_PROJECT_SCOPE
          } else {
            TargetStatus.IN_PROJECT_SCOPE
          }
        override val dependencies: Collection<Target>
          get() = buildInfo.javaInfo.dependencies.map { provider.getTarget(it) }

        override fun equals(other: Any?): Boolean = (other as? Target)?.label == label

        override fun hashCode(): Int = label.hashCode()
      }
    } else {
      null
    }
  }
}
