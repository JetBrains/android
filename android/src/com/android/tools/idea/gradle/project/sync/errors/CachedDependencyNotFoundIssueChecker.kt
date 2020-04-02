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
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData

import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CACHED_DEPENDENCY_NOT_FOUND

class CachedDependencyNotFoundIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (!message.startsWith("No cached version of ") || !message.contains("available for offline mode.")) return null

    // Log metrics.
    ApplicationManager.getApplication().invokeLater {
      updateUsageTracker(issueData.projectPath, CACHED_DEPENDENCY_NOT_FOUND)
    }

    val description = MessageComposer(message).apply {
      addQuickFix("Disable Gradle 'offline mode' and sync project", ToggleOfflineModeQuickFix(true))
    }
    return object : BuildIssue {
      override val title: String = "Cached dependency not found"
      override val description: String = description.buildMessage()
      override val quickFixes = description.quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}