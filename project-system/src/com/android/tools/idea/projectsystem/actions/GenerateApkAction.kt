/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.projectsystem.actions

import com.android.tools.idea.projectsystem.BuildApkActionToken
import com.android.tools.idea.projectsystem.getSyncManager
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class GenerateApkAction : AnAction(ACTION_TEXT) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || !TrustedProjects.isProjectTrusted(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val isSupported = BuildApkActionToken.isSupported(project)
    e.presentation.isEnabledAndVisible = isSupported
    if (isSupported) {
      val syncInProgress = project.getSyncManager().isSyncInProgress()
      e.presentation.isEnabled = !syncInProgress
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null && TrustedProjects.isProjectTrusted(project)) {
      BuildApkActionToken.execute(project)
    }
  }

  companion object {
    private const val ACTION_TEXT = "Generate APKs"
  }
}
