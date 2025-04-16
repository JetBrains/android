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
package com.google.idea.blaze.base.qsync.action

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt
import com.google.idea.blaze.exception.BuildException
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.asDeferred

/**
 * Action to build dependencies and enable analysis for the working set and it's reverse
 * dependencies
 */
class BuildDependenciesForWorkingSetAction : BlazeProjectAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun querySyncSupport(): QuerySyncStatus {
    return QuerySyncStatus.REQUIRED
  }

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    val helper = BuildDependenciesHelper(project)
    if (!helper.canEnableAnalysisNow()) {
      return
    }
    val workingSet = try {
      helper.workingSet
    } catch (be: BuildException) {
      logger.error("Error obtaining working set", be)
      notifyFailureWorkingSet(project, be)
      return
    }
    if (workingSet.isEmpty()) {
      logger.error("Empty working set")
      notifyEmptyWorkingSet(project)
      return
    }

    val querySyncActionStats =
      QuerySyncActionStatsScope.createForPaths(javaClass, e, ImmutableSet.copyOf(workingSet))

    helper.determineTargetsAndRun(
      workspaceRelativePaths = workingSet,
      disambiguateTargetPrompt = createDisambiguateTargetPrompt(PopupPositioner.showAtMousePointerOrCentered(e)),
      targetDisambiguationAnchors = TargetDisambiguationAnchors.NONE,
      querySyncActionStats = querySyncActionStats,
    ) { labels ->
      QuerySyncManager.getInstance(project)
        .enableAnalysisForReverseDeps(labels, querySyncActionStats, QuerySyncManager.TaskOrigin.USER_ACTION)
        .asDeferred()
    }
  }

  private fun notifyFailureWorkingSet(project: Project, e: Throwable) {
    QuerySyncManager.getInstance(project)
      .notifyError(
        "Could not obtain working set",
        "Encountered error when trying to get working set: ${e.message}"
      )
  }

  private fun notifyEmptyWorkingSet(project: Project) {
    QuerySyncManager.getInstance(project)
      .notifyWarning(
        "Nothing to build",
        "If you have edited project files recently, please re-sync and try again."
      )
  }

  companion object {
    private val logger = Logger.getInstance(BuildDependenciesForWorkingSetAction::class.java)
  }
}
