/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.bazel.BazelExitCode
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.OutputInfo
import com.google.idea.blaze.qsync.getCodeAnalysisDependencyGraphProvider
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.project.RequestedTargets
import com.google.idea.blaze.qsync.project.computeSufficientTargets
import com.google.idea.blaze.qsync.project.requiredTargets
import com.intellij.openapi.util.text.StringUtil
import java.io.IOException

/**
 * A file that tracks what files in the project can be analyzed and what is the status of their dependencies.
 *
 * The dependencies tracked for a target depends on its [DependencyTrackingBehavior], which is in turn determined by the source code
 * language of the target.
 */
class DependencyTrackerImpl(
  private val snapshotHolder: SnapshotHolder,
  override val builder: DependencyBuilder,
  private val artifactTracker: ArtifactTracker<BlazeContext>,
  private val querySyncUserPreferences: QuerySyncUserPreferences,
) : DependencyTracker {

  private fun getCurrentSnapshot(): QuerySyncProjectSnapshot {
    return snapshotHolder() ?: throw IllegalStateException("Sync is not yet complete")
  }

  /**
   * Builds the external dependencies of the given targets, putting the resultant libraries in the shared library directory so that they are
   * picked up by the IDE.
   */
  @Throws(IOException::class, BuildException::class)
  override fun buildDependenciesForTargets(context: BlazeContext, request: DependencyTracker.DependencyBuildRequest): Boolean {
    BuildDepsStatsScope.fromContext(context).orElse(null)?.setRequestedTargets(request.targets)
    val snapshot = getCurrentSnapshot()

    val requestedTargets = getRequestedTargets(snapshot, request)
    buildDependencies(context, snapshot, requestedTargets, request)
    return true
  }

  private fun getRequestedTargets(snapshot: QuerySyncProjectSnapshot, request: DependencyTracker.DependencyBuildRequest): RequestedTargets {
    return RequestedTargets(
      when (request.requestType) {
        DependencyTracker.DependencyBuildRequest.RequestType.SPECIAL_TARGETS -> request.targets
        DependencyTracker.DependencyBuildRequest.RequestType.MULTIPLE_TARGETS ->
          snapshot.staleGraph.computeSufficientTargets(
            request.targets,
            querySyncUserPreferences.experimentalBuildNativeTargetsFromAndroidTransitionPoint,
          )
        DependencyTracker.DependencyBuildRequest.RequestType.WHOLE_PROJECT -> snapshot.staleGraph.computeWholeProjectTargets()
      }
    )
  }

  @Throws(IOException::class, BuildException::class)
  private fun buildDependencies(
    context: BlazeContext,
    snapshot: QuerySyncProjectSnapshot,
    requestedTargets: RequestedTargets,
    request: DependencyTracker.DependencyBuildRequest,
  ) {
    BuildDepsStatsScope.fromContext(context).orElse(null)?.setBuildTargets(requestedTargets.targetsToBuild)
    val outputInfo = builder.build(context, requestedTargets.targetsToBuild, request.getOutputGroups(QuerySyncLanguage.entries))
    reportErrorsAndWarnings(context, snapshot, outputInfo)

    val requiredTargets = requestedTargets.requiredTargets(snapshot.getCodeAnalysisDependencyGraphProvider())
    artifactTracker.update(requiredTargets, outputInfo, context)
  }

  @Throws(NoDependenciesBuiltException::class)
  private fun reportErrorsAndWarnings(context: BlazeContext, snapshot: QuerySyncProjectSnapshot, outputInfo: OutputInfo) {
    if (outputInfo.isEmpty) {
      throw NoDependenciesBuiltException(
        "Build produced no usable outputs. Please fix any build errors and retry. If you" +
          " observe 'no such target' errors, your project may be out of sync. Please sync" +
          " the project and retry."
      )
    }

    if (outputInfo.targetsWithErrors.isNotEmpty()) {
      val projectDefinition = snapshot.projectDefinition
      context.setHasWarnings()
      val targetsByInclusion = outputInfo.targetsWithErrors.groupBy { projectDefinition.isIncluded(it) }

      val excludedErrors = targetsByInclusion[false].orEmpty()
      if (excludedErrors.isNotEmpty()) {
        context.output(
          PrintOutput.error(
            "%d external %s had build errors: \n  %s",
            excludedErrors.size,
            StringUtil.pluralize("dependency", excludedErrors.size),
            excludedErrors.take(10).joinToString("\n  "),
          )
        )
        if (excludedErrors.size > 10) {
          context.output(PrintOutput.log("and %d more.", excludedErrors.size - 10))
        }
      }

      val includedErrors = targetsByInclusion[true].orEmpty()
      if (includedErrors.isNotEmpty()) {
        context.output(
          PrintOutput.output(
            "%d project %s had build errors: \n  %s",
            includedErrors.size,
            StringUtil.pluralize("target", includedErrors.size),
            includedErrors.take(10).joinToString("\n  "),
          )
        )
        if (includedErrors.size > 10) {
          context.output(PrintOutput.log("and %d more.", includedErrors.size - 10))
        }
      }
    } else if (outputInfo.exitCode != BazelExitCode.SUCCESS) {
      // This will happen if there is an error in a build file, as no build actions are attempted
      // in that case.
      context.setHasWarnings()
      context.output(PrintOutput.error("There were build errors."))
    }
    if (context.hasWarnings()) {
      context.output(
        PrintOutput.error(
          "Your dependencies may be incomplete. If you see unresolved symbols, please fix the above build errors and try again."
        )
      )
      context.setHasWarnings()
    }
  }

  @Throws(BuildException::class)
  override fun updateDependenciesFromOutputInfo(context: BlazeContext, outputInfo: OutputInfo, targets: Set<Label>) {
    artifactTracker.update(targets, outputInfo, context)
  }
}
