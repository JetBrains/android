/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.AgpVersionsMismatch.Companion.INCOMPATIBLE_AGP_VERSIONS
import com.android.tools.idea.gradle.project.sync.AndroidSyncException
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler

class IncompatibleAgpVersionsIssueChecker: GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    if (rootCause !is AndroidSyncException) return null
    val message = rootCause.message ?: return null
    val matcher = INCOMPATIBLE_AGP_VERSIONS.matcher(message)
    if (!matcher.find()) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, AndroidStudioEvent.GradleSyncFailure.SDK_BUILD_TOOLS_TOO_LOW)
    }
    val messageLines = message.lines()
    if (messageLines.isEmpty()) return null
    return BuildIssueComposer("${messageLines[0]}\n${messageLines[1]}").composeBuildIssue()
  }

}