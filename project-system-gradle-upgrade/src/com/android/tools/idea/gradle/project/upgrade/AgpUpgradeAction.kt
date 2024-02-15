/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater

class AgpUpgradeAction: AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    @Suppress("UnstableApiUsage")
    if (project == null || !project.isTrusted() || project.getProjectSystem() !is GradleProjectSystem) {
      e.presentation.isEnabled = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val current = project.findPluginInfo()?.pluginVersion ?: return
    val latestKnown = AgpVersions.latestKnown
    ApplicationManager.getApplication().executeOnPooledThread {
      val published = IdeGoogleMavenRepository.getAgpVersions()
      val state = computeGradlePluginUpgradeState(current, latestKnown, published)
      invokeLater(ModalityState.nonModal()) {
        showAndInvokeAgpUpgradeRefactoringProcessor(project, current, state.target)
      }
    }
  }
}