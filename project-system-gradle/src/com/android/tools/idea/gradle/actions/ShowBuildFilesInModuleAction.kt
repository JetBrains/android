/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.projectView.ProjectToolWindowSettings
import com.android.tools.idea.navigator.ANDROID_VIEW_ID
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidViewShowBuildFilesInModuleEvent
import com.google.wireless.android.sdk.stats.AndroidViewShowBuildFilesInModuleEvent.ShowBuildFilesInModule
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.treeStructure.ProjectViewUpdateCause

class ShowBuildFilesInModuleAction: ToggleAction("Display Build Files In Module") {
  private val settings = ProjectToolWindowSettings.Companion.getInstance()

  override fun isSelected(e: AnActionEvent) = settings.showBuildFilesInModule

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (settings.showBuildFilesInModule != state) {
      settings.showBuildFilesInModule = state
      ProjectManager.getInstance().openProjects
        .filter { !it.isDisposed }
        .forEach{ ProjectView.getInstance(it)?.refresh(ProjectViewUpdateCause.SETTINGS) }
      trackShowBuildFileInModuleSettingChange(state)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    // Display this action only when flag is enabled and Android view is visible
    var showAction = false
    if (StudioFlags.SHOW_BUILD_FILES_IN_MODULE_SETTINGS.get()) {
      val project = e.project
      if (project != null) {
        val projectView = ProjectView.getInstance(project)
        showAction = projectView.currentProjectViewPane.id == ANDROID_VIEW_ID
      } else {
        showAction = false
      }
    }
    e.presentation.isEnabledAndVisible = showAction
    super.update(e)
  }

  private fun trackShowBuildFileInModuleSettingChange(settingValue: Boolean) {
    val showBuildFilesInModule = if (settingValue) ShowBuildFilesInModule.SHOW_BUILD_FILES_IN_MODULE else ShowBuildFilesInModule.DO_NOT_SHOW_BUILD_FILES_IN_MODULE
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ANDROID_VIEW_SHOW_BUILD_FILES_IN_MODULE_EVENT)
        .setAndroidViewShowBuildFilesInModuleEvent(
          AndroidViewShowBuildFilesInModuleEvent.newBuilder().apply {
          this.showBuildFilesInModule = showBuildFilesInModule
        }))
  }
}