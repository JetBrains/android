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
package com.google.idea.blaze.qsync.project.update

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.artifacts.ArtifactMetadata
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectProto

/**
 * Builds a [ProjectProto.Project] instances in a sequence of additive steps.
 */
interface ProjectProtoTransform {
  /**
   * Indicates which metadata are needed for the given target.
   *
   * @param forTarget The target in question, which has just been built.
   * @return A map of build artifacts to required metadata types. The keys in this map must
   * correspond to build artifacts from `forTarget`.
   */
  fun getRequiredArtifactMetadata(forTarget: TargetBuildInfo): Map<BuildArtifact, Set<ArtifactMetadata.Extractor<*>>>

  /**
   * Apply the transform.
   *
   * @param proto The existing project proto. This is derived from `graph` and may have had
   * other transforms applied to it.
   * @param graph The graph from which `proto` was derived from.
   * @param context Context.
   * @return A project proto instance to replace the existing one. May return `proto`
   * unchanged if this transform doesn't need to change anything.
   */
  @Throws(BuildException::class)
  fun apply(
    proto: ProjectProto.Project,
    graph: BuildGraphData,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ): ProjectProto.Project

  /**
   * Simple registry for transforms that also supports returning all transforms combined into one.
   */
  class Registry {
    private val transforms: MutableList<ProjectProtoTransform> = mutableListOf()

    fun add(transform: ProjectProtoTransform) {
      transforms.add(transform)
    }

    val composedTransform: ProjectProtoTransform
      get() = compose(transforms.toList())
  }

  companion object {
    fun compose(transforms: List<ProjectProtoTransform>): ProjectProtoTransform {
      return object : ProjectProtoTransform {
        override fun getRequiredArtifactMetadata(targetInfo: TargetBuildInfo): Map<BuildArtifact, Set<ArtifactMetadata.Extractor<*>>> {
          return buildMap {
            for (transform in transforms) {
              for ((artifact, metadata) in transform.getRequiredArtifactMetadata(targetInfo)) {
                compute(artifact) { k, v -> v.orEmpty() + metadata }
              }
            }
          }
        }

        @Throws(BuildException::class)
        override fun apply(
          proto: ProjectProto.Project,
          graph: BuildGraphData,
          artifactState: ArtifactTracker.State,
          context: Context<*>
        ): ProjectProto.Project {
          var proto = proto
          for (transform in transforms) {
            proto = transform.apply(proto, graph, artifactState, context)
          }
          return proto
        }
      }
    }
  }
}
