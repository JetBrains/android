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
import com.google.common.collect.Iterables
import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin
import com.google.idea.blaze.base.qsync.action.TargetDisambiguator.Companion.createDisambiguatorForFiles
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Arrays
import java.util.function.Consumer
import java.util.stream.Collectors

/** Action to build dependencies and enable analysis for all open editor tabs.  */
class BuildDependenciesForOpenFilesAction : BlazeProjectAction() {
  private val logger = Logger.getInstance(BuildDependenciesForOpenFilesAction::class.java)

  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.REQUIRED

  override fun actionPerformedInBlazeProject(project: Project, event: AnActionEvent) {
    val helper = BuildDependenciesHelper(project)
    if (!helper.canEnableAnalysisNow()) {
      return
    }
    // Each open source file may map to multiple targets, either because they're a build file
    // or because a source file is included in multiple targets.
    val openFiles =
      FileEditorManager.getInstance(project).allEditors
        .map { obj -> obj.getFile() }
        .toSet()
    val disambiguator = helper.createDisambiguatorForFiles(openFiles)
    val ambiguousTargets = disambiguator.calculateUnresolvableTargets()
    val querySyncActionStats =
      QuerySyncActionStatsScope.createForFiles(javaClass, event, ImmutableSet.copyOf(openFiles))

    val syncManager = QuerySyncManager.getInstance(project)
    if (ambiguousTargets.isEmpty()) {
      syncManager
        .enableAnalysis(
          disambiguator.unambiguousTargets,
          querySyncActionStats,
          TaskOrigin.USER_ACTION
        )
    } else if (ambiguousTargets.size == 1) {
      // there is a single ambiguous target set. Show the UI to disambiguate it.

      val ambiguousOne = ambiguousTargets.single()
      val displayFileName = ambiguousOne.displayLabel
      BuildDependenciesHelperSelectTargetPopup.chooseTargetToBuildFor(
        displayFileName,
        ambiguousOne,
        PopupPositioner.showAtMousePointerOrCentered(event),
        Consumer { chosen ->
          syncManager.enableAnalysis(
            ImmutableSet.builder<Label>()
              .addAll(disambiguator.unambiguousTargets)
              .add(chosen)
              .build(),
            querySyncActionStats,
            TaskOrigin.USER_ACTION
          )
        })
    } else {
      logger.warn(
        "Multiple ambiguous target sets for open files; not building them: "
        + ambiguousTargets.joinToString(separator = ", ") { it.displayLabel }
      )
      if (!disambiguator.unambiguousTargets.isEmpty()) {
        syncManager.enableAnalysis(
          disambiguator.unambiguousTargets,
          querySyncActionStats,
          TaskOrigin.USER_ACTION
        )
      } else {
        // TODO(mathewi) show an error?
        // or should we show multiple popups in parallel? (doesn't seem great if there are lots)
      }
    }
  }
}
