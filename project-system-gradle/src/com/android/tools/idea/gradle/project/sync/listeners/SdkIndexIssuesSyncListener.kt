/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.listeners

import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.util.progress.sleepCancellable
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting

/**
 * Notify users is there is any dependency with blocking issues in the project
 */
class SdkIndexIssuesSyncListener : GradleSyncListenerWithRoot {
  private var wasNotificationShown = false

  companion object {
    const val SDK_INDEX_NOTIFICATION_GROUP = "Google Play SDK Index Notifications"
  }

  override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
    getApplication().executeOnPooledThread {
      notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)
    }
  }

  override fun syncSkipped(project: Project) {
    getApplication().executeOnPooledThread {
      notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)
    }
  }

  @VisibleForTesting
  fun notifyBlockingIssuesIfNeeded(project: Project, sdkIndex: GooglePlaySdkIndex): Notification? {
    if (project.isDisposed) {
      return null
    }
    val dependencies = project.modules
      .asSequence()
      .map { GradleAndroidModel.get(it) }
      .filterNotNull()
      .map { it.mainArtifact.compileClasspath.libraries }
      .flatten()
      .filterIsInstance<IdeArtifactLibrary>()
      .mapNotNull { it.component }
      .toSet()
    // Wait for SDK Index to be ready up to one minute (it might need to download data)
    ProgressManager.getInstance().runProcess({
      var maxRetries = 60
      while (!sdkIndex.isReady() && maxRetries > 0) {
        sleepCancellable(1000)
        maxRetries -= 1
      }
    }, EmptyProgressIndicator())
    val numOfBlockingIssues = dependencies.count { library ->
      sdkIndex.hasLibraryBlockingIssues(library.group, library.name, library.version.toString())
    }
    if (numOfBlockingIssues > 0 && !wasNotificationShown) {
      val notification = SdkIndexNotification("There are $numOfBlockingIssues SDKs with warnings that will prevent app release in Google Play Console")
      val psdService = ProjectSettingsService.getInstance(project)
      if (psdService is AndroidProjectSettingsService) {
        val openSuggestionsAction = object : NotificationAction("Open Project Structure for details") {
          override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            psdService.openSuggestions()
          }
        }
        notification.addAction(openSuggestionsAction)
      }
      notification.notify(project)
      wasNotificationShown = true
      return notification
    }
    if (numOfBlockingIssues == 0) {
      // Since the project now has 0 issues, we would like to notify users as soon as they add one, set states as if we have not shown a notification
      wasNotificationShown = false
    }
    return null
  }

  class SdkIndexNotification(val message: String): Notification(SDK_INDEX_NOTIFICATION_GROUP, message, NotificationType.WARNING)
}