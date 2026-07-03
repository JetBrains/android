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
package com.google.idea.blaze.base.qsync

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.OutputGroup
import com.google.idea.blaze.qsync.deps.OutputInfo
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.common.experiments.BoolExperiment
import java.io.IOException
import java.util.EnumSet

/** A service that tracks what files in the project can be analyzed and what is the status of their dependencies. */
interface DependencyTracker {
  companion object {
    @JvmField val gatherJdeps = BoolExperiment("qsync.gather.jdeps", true)
  }

  /**
   * Builds the external dependencies of the given target(s), putting the resultant libraries in the shared library directory so that they
   * are picked up by the IDE.
   */
  @Throws(IOException::class, BuildException::class)
  fun buildDependenciesForTargets(context: BlazeContext, request: DependencyBuildRequest): Boolean

  /** Request to [buildDependenciesForTargets]. */
  class DependencyBuildRequest private constructor(@JvmField val requestType: RequestType, @JvmField val targets: Set<Label>) {
    enum class RequestType {
      SPECIAL_TARGETS,
      /** Build multiple targets and mark all dependencies as built even if they produce no artifacts. */
      MULTIPLE_TARGETS,
      /** Build the whole project and mark all dependencies as built even if they produce no artifacts. */
      WHOLE_PROJECT,
    }

    enum class OutputGroupRequestType {
      COMPILE_ONLY_OUTPUT_GROUPS,
      COMPILE_AND_RUNTIME_OUTPUT_GROUPS,
    }

    fun getOutputGroups(languages: Collection<QuerySyncLanguage>): Collection<OutputGroup> {
      return getOutputGroups(languages, OutputGroupRequestType.COMPILE_ONLY_OUTPUT_GROUPS)
    }

    companion object {
      @JvmStatic
      fun multiTarget(targets: Collection<Label>): DependencyBuildRequest {
        return DependencyBuildRequest(RequestType.MULTIPLE_TARGETS, targets.toSet())
      }

      @JvmStatic
      fun specialTarget(targets: Collection<Label>): DependencyBuildRequest {
        return DependencyBuildRequest(RequestType.SPECIAL_TARGETS, targets.toSet())
      }

      @JvmStatic
      fun wholeProject(): DependencyBuildRequest {
        return DependencyBuildRequest(RequestType.WHOLE_PROJECT, emptySet())
      }

      @JvmStatic
      fun getOutputGroups(languages: Collection<QuerySyncLanguage>, type: OutputGroupRequestType): Collection<OutputGroup> {
        val outputGroups = EnumSet.noneOf(OutputGroup::class.java)
        for (language in languages) {
          languageToOutputGroups(language, outputGroups::add)
        }

        if (type == OutputGroupRequestType.COMPILE_AND_RUNTIME_OUTPUT_GROUPS) {
          outputGroups.add(OutputGroup.TRANSITIVE_RUNTIME_JARS)
          outputGroups.add(OutputGroup.EXTERNAL_TRANSITIVE_RUNTIME_JARS)
        }

        return outputGroups
      }

      private fun languageToOutputGroups(language: QuerySyncLanguage, consumer: (OutputGroup) -> Boolean) {
        when (language) {
          QuerySyncLanguage.JVM -> {
            consumer(OutputGroup.JARS)
            consumer(OutputGroup.AARS)
            consumer(OutputGroup.GENSRCS)
            consumer(OutputGroup.ARTIFACT_INFO_FILE)
            if (gatherJdeps.value) {
              consumer(OutputGroup.JDEPS)
            }
          }
          QuerySyncLanguage.CC -> {
            consumer(OutputGroup.CC_GEN_HEADERS)
            consumer(OutputGroup.CC_INFO_FILE)
          }
        }
      }
    }
  }

  val builder: DependencyBuilder

  /** Updates the artifact tracker with the given build output, enabling code analysis for the given targets. */
  @Throws(BuildException::class) fun updateDependenciesFromOutputInfo(context: BlazeContext, outputInfo: OutputInfo, targets: Set<Label>)
}
