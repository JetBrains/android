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

import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class CorruptGradleDependencyIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (message.isEmpty() || !message.startsWith("Premature end of Content-Length delimited message body")) return null

    // Log metrics.
    invokeLater {
      SyncErrorHandler.updateUsageTracker(issueData.projectPath, GradleSyncFailure.CORRUPT_GRADLE_DEPENDENCY)
    }

    val syncProjectQuickFix = ClassLoadingIssueChecker.SyncProjectQuickFix()
    return object : BuildIssue {
      override val title: String = "Gradle's dependency cache seems to be corrupt or out of sync."
      override val description: String = buildString {
        appendln(message)
        appendln("\n<a href=\"${syncProjectQuickFix.id}\">Re-download dependencies and sync project (requires network)</a>")
      }
      override val quickFixes: List<BuildIssueQuickFix> = listOf(syncProjectQuickFix)
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}