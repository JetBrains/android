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

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.net.SocketException
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CONNECTION_DENIED
import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler


class ConnectionPermissionDeniedIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (rootCause !is SocketException || message.isEmpty() || !message.contains("Permission denied: connect")) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, CONNECTION_DENIED)
    }

    val description = MessageComposer(message).apply {
      addQuickFix("More details (and potential fix)",
                  OpenLinkQuickFix("https://developer.android.com/studio/troubleshoot.html#project-sync"))
    }
    return object : BuildIssue {
      override val title: String = "Connection to the Internet denied."
      override val description: String = description.buildMessage()
      override val quickFixes: List<BuildIssueQuickFix> = description.quickFixes
      override fun getNavigatable(project: Project): Navigatable?  = null
    }
  }
}