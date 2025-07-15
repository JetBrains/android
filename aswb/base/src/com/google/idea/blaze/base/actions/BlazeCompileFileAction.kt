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

import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.build.BlazeBuildService
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap
import com.google.idea.common.actions.ActionPresentationHelper
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import java.io.File
import kotlinx.coroutines.guava.asDeferred

internal class BlazeCompileFileAction : BlazeProjectAction() {
  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.SUPPORTED

  override fun updateForBlazeProject(project: Project, e: AnActionEvent) {
    ActionPresentationHelper.of(e)
      .disableIf(!isEnabled(project, e))
      .setTextWithSubject(
        "Compile File",
        "Compile %s",
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.firstOrNull()
      )
      .disableWithoutSubject()
      .commit()
  }

  private fun isEnabled(project: Project, e: AnActionEvent): Boolean {
    if (Blaze.getProjectType(project) == BlazeImportSettings.ProjectType.QUERY_SYNC) {
      val vfs = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.takeUnless { it.isEmpty() }?.toList() ?: return false
      val querySyncManager = QuerySyncManager.getInstance(project)
      return querySyncManager.getLoadedProject().isPresent
        &&
             // TODO: b/411054914 - Build dependencies actions should not get disabled when not in sync/not in a project target and instead
             // they should automatically trigger sync.
             querySyncManager
               .getTargetsToBuildByPaths(WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, vfs))
          .asSequence()
          .flatMap { it.targets }.any()
    }
    return getTargets(e).isNotEmpty()
  }

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filterNotNull()?.takeUnless { it.isEmpty() } ?: return

    if (Blaze.getProjectType(project) == BlazeImportSettings.ProjectType.QUERY_SYNC) {
      val buildDependenciesHelper = BuildDependenciesHelper(project)
      val querySyncActionStats = QuerySyncActionStatsScope.createForFiles(javaClass, e, ImmutableList.copyOf(files))
      buildDependenciesHelper.determineTargetsAndRun(
        workspaceRelativePaths = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, files),
        disambiguateTargetPrompt = createDisambiguateTargetPrompt({ popup -> popup.showCenteredInCurrentWindow(project) }),
        targetDisambiguationAnchors = TargetDisambiguationAnchors.NONE,
        querySyncActionStats = querySyncActionStats,
      ) { labels ->
        BlazeBuildService.getInstance(project)
          .buildFileForLabels(files.joinToString(", ", limit = 2), ImmutableSet.copyOf(labels))
          .asDeferred()
      }
      return
    }

    BlazeBuildService.getInstance(project).buildFile(files[0].name, getTargets(e))
  }

  private fun getTargets(e: AnActionEvent): ImmutableCollection<Label> {
    val project = e.project
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project != null && virtualFile != null) {
      return SourceToTargetMap.getInstance(project)
        .getTargetsToBuildForSourceFile(File(virtualFile.path))
    }
    return ImmutableList.of()
  }
}
