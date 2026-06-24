/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.build.BlazeBuildService
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors
import com.google.idea.common.actions.ActionPresentationHelper
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.asDeferred

internal class BlazeCompileFileAction : BlazeProjectAction() {
  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.SUPPORTED

  override fun updateForBlazeProject(project: Project, e: AnActionEvent) {
    ActionPresentationHelper.of(e)
      .disableIf(!isEnabled(project, e))
      .setTextWithSubject("Compile File", "Compile %s", e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.firstOrNull())
      .disableWithoutSubject()
      .commit()
  }

  private fun isEnabled(project: Project, e: AnActionEvent): Boolean {
    return QuerySyncManager.getInstance(project).getLoadedProject().isPresent
  }

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filterNotNull()?.takeUnless { it.isEmpty() } ?: return

    val buildDependenciesHelper = BuildDependenciesHelper(project)
    val querySyncActionStats = QuerySyncActionStatsScope.createForFiles(project, javaClass, e, ImmutableList.copyOf(files))
    buildDependenciesHelper.determineTargetsAndRun(
      workspaceRelativePaths = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, files),
      disambiguateTargetPrompt = createDisambiguateTargetPrompt({ popup -> popup.showCenteredInCurrentWindow(project) }),
      targetDisambiguationAnchors = TargetDisambiguationAnchors.NONE,
      querySyncActionStats = querySyncActionStats,
    ) { labels ->
      BlazeBuildService.getInstance(project).buildFileForLabels(files.joinToString(", ", limit = 2), labels).asDeferred()
    }
  }
}
