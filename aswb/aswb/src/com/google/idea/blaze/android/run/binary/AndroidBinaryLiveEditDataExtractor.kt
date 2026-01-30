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
package com.google.idea.blaze.android.run.binary

import com.android.tools.idea.run.classes.BuildOutcome
import com.android.tools.idea.run.classes.BuildOutcomeCache
import com.google.idea.blaze.android.run.runner.LiveEditDataExtractor
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.qsync.DependencyBuilder
import com.google.idea.blaze.base.qsync.DependencyTracker.DependencyBuildRequest
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.intellij.openapi.project.Project
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.runBlocking

class AndroidBinaryLiveEditDataExtractor(
  private val project: Project,
  private val binaryTarget: Label
): LiveEditDataExtractor {
  private var preparedInvocation: DependencyBuilder.PreparedInvocation? = null
  private val outcome: CompletableDeferred<BuildOutcome> = CompletableDeferred()

  override fun prepareInvocation(
    context: BlazeContext,
    buildInvoker: BuildSystem.BuildInvoker,
    commandBuilder: BlazeCommand.Builder,
  ) {
    val dependencyBuilder = QuerySyncManager.getInstance(project).assertProjectLoaded().dependencyBuilder
    val outputGroups =
      DependencyBuildRequest.getOutputGroups(listOf(QuerySyncLanguage.JVM), DependencyBuildRequest.RequestType.LIVE_EDIT_BUILD_APK)
    val invocation = dependencyBuilder
      .prepareInvocation(
        context,
        maybeBuildTargets = emptySet(), // This is supported by the dependency builder.
        outputGroups,
        replaceOutputGroups = false,
        buildInvoker
      )
    invocation.updateCommand(commandBuilder)
    this.preparedInvocation = invocation
  }

  override fun blockingExtract(context: BlazeContext, buildOutputs: BlazeBuildOutputs) {
    outcome.completeWith(
      runCatching {
        val invocation = preparedInvocation ?: error("Iternal error: blockingExtract extract called too soon")
        val outputInfo = invocation.createOutputInfo(buildOutputs, Instant.now(), context)
        BuildOutcomeCache.buildOutcome(project, binaryTarget, outputInfo, context)
      }
    )
  }

  override fun getBuildOutcomeBlocking(): BuildOutcome {
    return runBlocking { outcome.await() }
  }
}