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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.util.function.Consumer

class InternetConnectionIssueChecker : GradleIssueChecker {
  private val COULD_NOT_GET = "Could not GET "
  private val COULD_NOT_HEAD = "Could not HEAD "
  private val NETWORK_UNREACHABLE = "Network is unreachable"

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (!message.startsWith(COULD_NOT_GET) && !message.startsWith(COULD_NOT_HEAD) && !message.startsWith(NETWORK_UNREACHABLE)) return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, GradleSyncFailure.INTERNET_CONNECTION_ERROR)
    return BuildIssueComposer(message).apply {
      val project = fetchIdeaProjectForGradleProject(issueData.projectPath) ?: return@apply
      if (GradleSettings.getInstance(project).isOfflineWork) {
        addQuickFix("Disable Gradle 'offline mode' and sync project", ToggleOfflineModeQuickFix(false))
      }
    }.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return failureCause.startsWith(COULD_NOT_GET) || failureCause.startsWith(COULD_NOT_HEAD) || failureCause.startsWith(NETWORK_UNREACHABLE)
  }
}