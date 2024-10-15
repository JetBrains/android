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
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.quickFixes.InstallBuildToolsQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.MISSING_BUILD_TOOLS
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * This IssueChecker is for olg AGP version where having an old build tools version ends up in a sync issue. For newer AGP (tested for 3.6
 * and above, a low build tools version will be ignored and the default minimum version required by the SDK handler will be chosen.
 */
class MissingBuildToolsIssueChecker: GradleIssueChecker {
  private val MISSING_BUILD_TOOLS_PATTERN = Pattern.compile("(Cause: )?([Ff])ailed to find Build Tools revision (.*)")
  private val EXCEPTION_ILLEGAL_PATTERN = Pattern.compile("Caused by: java.lang.IllegalStateException(.*)")
  private val EXCEPTION_EXTERNAL_SYSTEM_PATTERN =
    Pattern.compile("Caused by: com.intellij.openapi.externalSystem.model.ExternalSystemException(.*)")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (message.isBlank() || rootCause !is IllegalStateException && rootCause !is ExternalSystemException) return null
    val matcher = MISSING_BUILD_TOOLS_PATTERN.matcher(message.lines()[0])
    if (!matcher.matches()) return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, MISSING_BUILD_TOOLS)
    val version = matcher.group(3)
    val buildIssueComposer = getBuildIssueDescription(message, version)
    return buildIssueComposer.composeBuildIssue()
  }

  private fun getBuildIssueDescription(message: String, version: String): BuildIssueComposer {
    val buildIssueComposer = BuildIssueComposer(message)
    buildIssueComposer.addQuickFix("Install Build Tools $version and sync project",
                                   InstallBuildToolsQuickFix(version, emptyList(), false))
    return buildIssueComposer
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return stacktrace != null &&
           (EXCEPTION_ILLEGAL_PATTERN.matcher(stacktrace).find() || EXCEPTION_EXTERNAL_SYSTEM_PATTERN.matcher(stacktrace).find()) &&
           MISSING_BUILD_TOOLS_PATTERN.matcher(failureCause.lines()[0]).matches()
  }
}