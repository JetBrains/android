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

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.SdkIndexProjectStats
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting

/**
 * Notify users is there is any dependency with blocking issues in the project
 */
class SdkIndexIssuesSyncListener(private val coroutineScope: CoroutineScope) : GradleSyncListenerWithRoot {
  @VisibleForTesting
  var wasNotificationShown = false

  companion object {
    const val SDK_INDEX_NOTIFICATION_GROUP = "Google Play SDK Index Notifications"
  }

  override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
    checkForFlagAndNotify(project)
  }

  override fun syncSkipped(project: Project) {
    checkForFlagAndNotify(project)
  }

  private fun checkForFlagAndNotify(project: Project) {
    if (StudioFlags.SHOW_SUMMARY_NOTIFICATION.get()) {
      coroutineScope.launch {
        val notification = notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)
        if (application.isUnitTestMode) {
          project.service<EventStreamForTesting>().notifications.send(notification)
        }
      }
    }
    else {
      wasNotificationShown = false
    }
  }

  private suspend fun notifyBlockingIssuesIfNeeded(project: Project, sdkIndex: GooglePlaySdkIndex): Notification? {
    // SdkIndexIssuesSyncListenerTest.`Disposed project does not show notification` spy project isDisposed returns need to be changed if
    // this function calls to isDisposed changes
    if (project.isDisposed) {
      return null
    }
    // Wait for SDK Index to be ready up to one minute (it might need to download data)
    var maxRetries = 60
    while (!sdkIndex.isReady() && maxRetries > 0) {
      delay(1000)
      maxRetries -= 1
      if (project.isDisposed) {
        break
      }
    }
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
    var numErrorsAndWarnings = 0
    var numBlockingIssues = 0
    var numPolicyIssues = 0
    var numCriticalIssues = 0
    var numVulnerabilities = 0
    var numOutdatedIssues = 0
    dependencies.forEach { library->
      val version = library.version.toString()
      if (sdkIndex.hasLibraryErrorOrWarning(library.group, library.name, version)) {
        numErrorsAndWarnings++
      }
      if (sdkIndex.hasLibraryBlockingIssues(library.group, library.name, version)) {
        numBlockingIssues++
      }
      if (sdkIndex.isLibraryNonCompliant(library.group, library.name, version, null)) {
        numPolicyIssues++
      }
      if (sdkIndex.hasLibraryCriticalIssues(library.group, library.name, version, null)) {
        numCriticalIssues++
      }
      if (sdkIndex.hasLibraryVulnerabilityIssues(library.group, library.name, version, null)) {
        numVulnerabilities++
      }
      if (sdkIndex.isLibraryOutdated(library.group, library.name, version, null)) {
        numOutdatedIssues++
      }
    }
    // Report stats even if not presented to user
    reportProjectStats(project, numErrorsAndWarnings, numBlockingIssues, numPolicyIssues, numCriticalIssues, numVulnerabilities, numOutdatedIssues)
    if (numBlockingIssues > 0 && !wasNotificationShown) {
      val notification = SdkIndexNotification("There are $numBlockingIssues SDKs with warnings that will prevent app release in Google Play Console")
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
    if (numBlockingIssues == 0) {
      // Since the project now has 0 issues, we would like to notify users as soon as they add one, set states as if we have not shown a notification
      wasNotificationShown = false
    }
    return null
  }

  private fun reportProjectStats(project: Project, numErrorsAndWarnings: Int, numBlockingIssues: Int, numPolicyIssues: Int, numCriticalIssues: Int, numVulnerabilities: Int, numOutdatedIssues: Int) {
    val event = AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.GOOGLE_PLAY_SDK_INDEX)
      .setKind(AndroidStudioEvent.EventKind.SDK_INDEX_PROJECT_STATS)
      .withProjectId(project)
      .setSdkIndexProjectStats(
        SdkIndexProjectStats.newBuilder()
          .setNumErrorsAndWarnings(numErrorsAndWarnings)
          .setNumBlockingIssues(numBlockingIssues)
          .setNumPolicyIssues(numPolicyIssues)
          .setNumCriticalIssues(numCriticalIssues)
          .setNumVulnerabilityIssues(numVulnerabilities)
          .setNumOutdatedIssues(numOutdatedIssues)
      )
    UsageTracker.log(event)
  }

  class SdkIndexNotification(val message: String): Notification(SDK_INDEX_NOTIFICATION_GROUP, message, NotificationType.WARNING)

  @VisibleForTesting
  @Service(Service.Level.PROJECT)
  class EventStreamForTesting {
    val notifications = Channel<Notification?>(capacity = 1, onBufferOverflow = DROP_OLDEST)
  }
}
