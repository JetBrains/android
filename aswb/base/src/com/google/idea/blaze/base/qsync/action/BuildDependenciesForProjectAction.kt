/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.QuerySyncManager.Companion.getInstance
import com.google.idea.blaze.base.qsync.QuerySyncProject
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.util.function.Function
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Build all dependencies for the whole project and enables analysis for all targets.
 *
 *
 * Since this action may cause problems for large projects, warns the users and requests
 * confirmation if the project is large.
 */
class BuildDependenciesForProjectAction : BlazeProjectAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.REQUIRED

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent?) {
    val syncMan = getInstance(project)
    val snapshot =
      syncMan
        .currentSnapshot
        .orElse(null)
    if (snapshot == null) {
      syncMan.notifyError("Project not loaded", "Cannot enable analysis before project is loaded.")
      return
    }

    val externalDeps = snapshot.graph().getExternalDependencyCount()
    logger.warn("Total external deps: $externalDeps")
    if (externalDeps > EXTERNAL_DEPS_WARNING_THRESHOLD) {
      if (!WarningDialog(project).showAndGet()) {
        return
      }
    }

    val querySyncActionStats =
      QuerySyncActionStatsScope.create(this.javaClass, e)
    syncMan.enableAnalysisForWholeProject(
      querySyncActionStats,
      QuerySyncManager.TaskOrigin.USER_ACTION
    )
  }

  internal class WarningDialog(project: Project) : DialogWrapper(project) {
    init {
      init()
      title = "Project is Large"
      isModal = true
    }

    override fun createCenterPanel(): JComponent {
      return JLabel(
        "Your project is large. Enabling analysis for the whole project may fail,\n"
          + "be very slow, or make the IDE slow to use afterwards. Are you sure?"
      )
    }

    override fun createActions(): Array<Action> {
      return arrayOf(okAction, cancelAction)
    }
  }

  companion object {
    private val logger = Logger.getInstance(BuildDependenciesForProjectAction::class.java)

    private const val EXTERNAL_DEPS_WARNING_THRESHOLD = 10000
  }
}
