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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.EditorNotifications
import com.intellij.util.ThreeState

/**
 * This action is equivalent to pressing the "Ignore these changes" button within the
 * [com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider.StaleGradleModelNotificationPanel]
 *
 * It will only be enabled when the actual "Ignore these changes" action and its corresponding
 * notification are visible. We use the same conditions that the notification panel uses to
 * determine visibility.
 */
class IgnoreGradleChangesAction : AnAction(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val isGradleSyncDue = (project.getProjectSystem() is GradleProjectSystem) && GradleSyncState.getInstance(project).isSyncNeeded() == ThreeState.YES
    e.presentation.isEnabled = isGradleSyncDue && !ProjectSyncStatusNotificationProvider.shouldHideBanner()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    GradleSyncStateHolder.getInstance(project).ignoreChangedFiles();
    EditorNotifications.getInstance(project).updateAllNotifications()
  }
}