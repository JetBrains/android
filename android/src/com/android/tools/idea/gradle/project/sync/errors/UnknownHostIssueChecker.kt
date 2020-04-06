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

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.net.UnknownHostException

class UnknownHostIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (message.isEmpty() || issueData.error !is UnknownHostException) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.UNKNOWN_HOST)
    }

    val ideaProject = fetchIdeaProjectForGradleProject(issueData.projectPath)
    val description = MessageComposer("Unknown host '$message'. You may need to adjust the proxy settings in Gradle.").apply {
      if (ideaProject != null && !GradleSettings.getInstance(ideaProject).isOfflineWork) {
        addQuickFix("Enable Gradle 'offline mode' and sync project",  ToggleOfflineModeQuickFix(true))
      }
      addQuickFix("Learn about configuring HTTP proxies in Gradle",
                  OpenLinkQuickFix("https://docs.gradle.org/current/userguide/userguide_single.html#sec:accessing_the_web_via_a_proxy"))
    }
    return object : BuildIssue {
      override val title = "Gradle Sync issues."
      override val description = description.buildMessage()
      override val quickFixes = description.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }
}