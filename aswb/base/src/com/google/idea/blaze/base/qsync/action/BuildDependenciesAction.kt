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
import com.google.idea.blaze.base.qsync.QuerySync
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt
import com.ibm.icu.lang.UCharacter
import com.ibm.icu.text.BreakIterator
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.util.Locale
import kotlinx.coroutines.guava.asDeferred

/**
 * Action to build dependencies and enable analysis.
 *
 *
 * It can operate on a source file, BUILD file or package. See [ ][BuildGraphDataImpl.getProjectTargets] for a
 * description of what targets dependencies aren built for in each case.
 */
class BuildDependenciesAction : BlazeProjectAction() {
  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.REQUIRED

  override fun updateForBlazeProject(project: Project, e: AnActionEvent) {
    val presentation = e.presentation
    presentation.setIcon(AllIcons.Actions.Compile)
    presentation.setText(if (e.place == ActionPlaces.MAIN_MENU) "$NAME for Current File" else NAME)
    val vfs = e.getVirtualFiles() ?: let {
      presentation.setEnabled(false)
      return
    }
    val helper = BuildDependenciesHelper(project)
    // TODO: b/411054914 - Build dependencies actions should not get disabled when not in sync/not in a project target and instead
    // they should automatically trigger sync.
    if (vfs.all { !helper.canEnableAnalysisFor(it) }) {
      presentation.setEnabled(false)
      return
    }
    if (!helper.canEnableAnalysisNow()) {
      presentation.setEnabled(false)
      return
    }
    presentation.setEnabled(true)
  }

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    val helper = BuildDependenciesHelper(project)
    val vfs = e.getVirtualFiles() ?: return
    val querySyncActionStats = QuerySyncActionStatsScope.createForFiles(javaClass, e, ImmutableList.copyOf(vfs))
    helper.determineTargetsAndRun(
      workspaceRelativePaths = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, vfs),
      disambiguateTargetPrompt = createDisambiguateTargetPrompt(PopupPositioner.showAtMousePointerOrCentered(e)),
      targetDisambiguationAnchors = TargetDisambiguationAnchors.WorkingSet(helper)
    ) { labels ->
      QuerySyncManager.getInstance(project)
        .enableAnalysis(labels + helper.workingSetTargetsIfEnabled, querySyncActionStats, TaskOrigin.USER_ACTION)
        .asDeferred()
    }
  }

  companion object {
    val NAME: String = UCharacter.toTitleCase(
      Locale.US, QuerySync.BUILD_DEPENDENCIES_ACTION_NAME, BreakIterator.getWordInstance()
    )
  }
}
