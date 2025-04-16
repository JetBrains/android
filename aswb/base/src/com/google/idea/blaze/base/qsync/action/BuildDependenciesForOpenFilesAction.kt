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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Action to build dependencies and enable analysis for all open editor tabs.
 */
class BuildDependenciesForOpenFilesAction : BlazeProjectAction() {
  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.REQUIRED

  override fun actionPerformedInBlazeProject(project: Project, event: AnActionEvent?) {
    val helper = BuildDependenciesHelper(project)
    val syncManager = QuerySyncManager.getInstance(project)
    if (!helper.canEnableAnalysisNow()) {
      return
    }
    // Each open source file may map to multiple targets, either because they're a build file
    // or because a source file is included in multiple targets.
    val openFiles = FileEditorManager.getInstance(project).allEditors.map { it.file }
    val querySyncActionStats =
      QuerySyncActionStatsScope.createForFiles(javaClass, event, ImmutableList.copyOf(openFiles))

    helper.determineTargetsAndRun(
      workspaceRelativePaths = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, openFiles),
      positioner = PopupPositioner.showAtMousePointerOrCentered(event),
      targetDisambiguationAnchors = TargetDisambiguationAnchors.NONE
    ) { labels ->
      syncManager.enableAnalysis(labels, querySyncActionStats, QuerySyncManager.TaskOrigin.USER_ACTION)
    }
  }
}
