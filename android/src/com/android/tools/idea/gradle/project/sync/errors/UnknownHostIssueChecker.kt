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
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.net.UnknownHostException
import java.util.function.Consumer
import java.util.regex.Pattern

class UnknownHostIssueChecker: GradleIssueChecker {
  private val UNKNOWN_HOST_PATTERN = Pattern.compile("java.net.UnknownHostException(.*)")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (message.isBlank() || rootCause !is UnknownHostException) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.UNKNOWN_HOST)
    }

    val ideaProject = fetchIdeaProjectForGradleProject(issueData.projectPath)
    return BuildIssueComposer("Unknown host '$message'. You may need to adjust the proxy settings in Gradle.").apply {
      if (ideaProject != null && !GradleSettings.getInstance(ideaProject).isOfflineWork) {
        addQuickFix("Enable Gradle 'offline mode' and sync project",  ToggleOfflineModeQuickFix(true))
      }
      addQuickFix("Learn about configuring HTTP proxies in Gradle",
                  OpenLinkQuickFix("https://docs.gradle.org/current/userguide/userguide_single.html#sec:accessing_the_web_via_a_proxy"))
    }.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return stacktrace != null && UNKNOWN_HOST_PATTERN.matcher(stacktrace).find()
  }
}