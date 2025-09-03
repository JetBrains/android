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

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.asDeferred

private val NAME = BuildDependenciesAction.NAME + " for Reverse Dependencies"
/** Action to build dependencies and enable analysis for a file and it's reverse dependencies  */
class BuildDependenciesForDirectReverseDepsAction : BlazeProjectAction() {
  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.REQUIRED

  override fun updateForBlazeProject(project: Project, e: AnActionEvent) {
    e.presentation.isEnabled = e.getVirtualFiles() != null
    e.presentation.setText(if (e.place == ActionPlaces.MAIN_MENU) "$NAME of Current File" else NAME)
  }

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    val vfs = e.getVirtualFiles() ?: return
    val helper = BuildDependenciesHelper(project)
    if (!helper.canEnableAnalysisNow()) {
      return
    }

    if (vfs.all { !helper.canEnableAnalysisFor(it)} ) {
      return
    }

    val querySyncActionStats = QuerySyncActionStatsScope.createForFiles(project, javaClass, e, ImmutableList.copyOf(vfs))
    helper.determineTargetsAndRun(
      workspaceRelativePaths = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, vfs),
      disambiguateTargetPrompt = createDisambiguateTargetPrompt(PopupPositioner.showAtMousePointerOrCentered(e)),
      targetDisambiguationAnchors = TargetDisambiguationAnchors.NONE,
      querySyncActionStats = querySyncActionStats,
    ) { labels ->
      QuerySyncManager.getInstance(project).enableAnalysisForReverseDeps(
        labels,
        querySyncActionStats,
        QuerySyncManager.TaskOrigin.USER_ACTION
      )
        .asDeferred()
    }
  }
}
