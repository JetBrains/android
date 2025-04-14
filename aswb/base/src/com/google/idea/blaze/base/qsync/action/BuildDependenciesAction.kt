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

import com.google.common.collect.Sets
import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.qsync.QuerySync
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.ibm.icu.lang.UCharacter
import com.ibm.icu.text.BreakIterator
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import java.util.Locale
import java.util.function.Consumer

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
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: let {
      presentation.setEnabled(false)
      return
    }
    val helper = BuildDependenciesHelper(project)
    if (!helper.canEnableAnalysisFor(virtualFile)) {
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
    val vfile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val querySyncActionStats = QuerySyncActionStatsScope.createForFile(javaClass, e, vfile)
    helper.determineTargetsAndRun(
      vfile,
      PopupPositioner.showAtMousePointerOrCentered(e),
      Consumer { labels ->
        QuerySyncManager.getInstance(project)
          .enableAnalysis(
            Sets.union(labels, helper.workingSetTargetsIfEnabled), querySyncActionStats,
            QuerySyncManager.TaskOrigin.USER_ACTION
          )
      },
      BuildDependenciesHelper.TargetDisambiguationAnchors.WorkingSet(helper)
    )
  }

  companion object {
    @JvmField
    val NAME: String = UCharacter.toTitleCase(
      Locale.US, QuerySync.BUILD_DEPENDENCIES_ACTION_NAME, BreakIterator.getWordInstance()
    )
  }
}
