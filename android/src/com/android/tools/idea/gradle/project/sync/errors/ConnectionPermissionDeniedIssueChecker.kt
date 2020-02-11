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
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.net.SocketException
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CONNECTION_DENIED
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer
import java.util.regex.Pattern


class ConnectionPermissionDeniedIssueChecker: GradleIssueChecker {
  private val SOCKET_EXCEPTION_PATTERN = Pattern.compile("Caused by: java.net.SocketException(.*)")
  private val PERMISSION_DENIED = "Permission denied: connect"

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (rootCause !is SocketException || message.isBlank() || !message.contains(PERMISSION_DENIED)) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, CONNECTION_DENIED)
    }

    return BuildIssueComposer("Connection to the Internet denied.").apply {
      addQuickFix("More details (and potential fix)",
                  OpenLinkQuickFix("https://developer.android.com/studio/troubleshoot.html#project-sync"))
    }.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return stacktrace != null && SOCKET_EXCEPTION_PATTERN.matcher(stacktrace).find() && failureCause.contains(PERMISSION_DENIED)
  }
}