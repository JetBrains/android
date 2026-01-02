/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.project

import com.android.tools.idea.concurrency.addCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.idea.blaze.base.build.BlazeBuildService
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot.virtualFilesToWorkspaceRelativePaths
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors
import com.google.idea.blaze.base.qsync.entity.BazelEntitySource
import com.google.idea.blaze.base.settings.Bazel.isBazelProject
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ModuleFilesBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.TaskRunnerResults
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.guava.asListenableFuture
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * A [ProjectTaskRunner] that knows how to perform standard operations like build, re-build, compile a file in Bazel projects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UnstableApiUsage")
class BazelProjectTaskRunner: ProjectTaskRunner() {
  override fun canRun(task: ProjectTask): Boolean = error("not expected")

  override fun canRun(
    project: Project,
    projectTask: ProjectTask,
    context: ProjectTaskContext?,
  ): Boolean {
    if (!project.isBazelProject()) return false
    fun Module.isOutModule() = findModuleEntity()?.entitySource == BazelEntitySource
    return when(projectTask) {
      is ModuleFilesBuildTask -> projectTask.module.isOutModule()
      is ModuleBuildTask -> projectTask.module.isOutModule()
      else -> false
    }
  }

  override fun run(
    project: Project,
    context: ProjectTaskContext,
    vararg tasks: ProjectTask,
  ): Promise<Result> {
    return when {
      tasks.any { it is ModuleBuildTask && it !is ModuleFilesBuildTask } -> BlazeBuildService.getInstance(project).buildProject()
      tasks.any { it is ModuleFilesBuildTask } -> BuildDependenciesHelper(project).determineTargetsAndRun(
        workspaceRelativePaths = virtualFilesToWorkspaceRelativePaths(
          project,
          tasks.filterIsInstance<ModuleFilesBuildTask>().flatMap { it.files.toList() }
        ),
        disambiguateTargetPrompt = createDisambiguateTargetPrompt({ it.showCenteredInCurrentWindow(project)}),
        targetDisambiguationAnchors = TargetDisambiguationAnchors.NONE,
        querySyncActionStats = QuerySyncActionStatsScope.create(project, this.javaClass, null)
      ) {
        labels -> BlazeBuildService.getInstance(project).buildFileForLabels("", labels).asDeferred()
      }.asListenableFuture()
      else -> Futures.immediateFailedFuture<Boolean>(IllegalStateException("unexpected"))
    }.toPromise { if (it) TaskRunnerResults.SUCCESS else TaskRunnerResults.FAILURE }
  }
}

private fun <T: Any, R> ListenableFuture<T>.toPromise(transform: (T) -> R): AsyncPromise<R> {
  val result = AsyncPromise<R>()
  this.addCallback(
    directExecutor(),
    success = { invocationResult ->
      result.setResult(invocationResult?.let(transform))
    },
    failure = { result.setError(it) }
  )
  return result
}
