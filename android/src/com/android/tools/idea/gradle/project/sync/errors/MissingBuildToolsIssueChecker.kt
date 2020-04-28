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

import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.GradleVersion.tryParseAndroidGradlePluginVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.findFromBuildFiles
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.InstallBuildToolsQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.MISSING_BUILD_TOOLS
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.regex.Pattern

/**
 * This IssueChecker is for olg AGP version where having an old build tools version ends up in a sync issue. For newer AGP (tested for 3.6
 * and above, a low build tools version will be ignored and the default minimum version required by the SDK handler will be chosen.
 */
class MissingBuildToolsIssueChecker: GradleIssueChecker {
  private val MISSING_BUILD_TOOLS_PATTERN = Pattern.compile("(Cause: )?([Ff])ailed to find Build Tools revision (.*)")
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (message.isBlank() ||
        issueData.error !is IllegalStateException && issueData.error !is ExternalSystemException) return null
    val matcher = MISSING_BUILD_TOOLS_PATTERN.matcher(message.lines()[0])
    if (!matcher.matches()) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, MISSING_BUILD_TOOLS)
    }
    val version = matcher.group(3)
    val description = getBuildIssueDescription(message, issueData.projectPath, version)
    return object : BuildIssue {
      override val title = "Gradle Sync issues."
      override val description = description.buildMessage()
      override val quickFixes = description.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }

  private fun getBuildIssueDescription(message: String, projectPath: String, version: String): MessageComposer {
    val description = MessageComposer(message)
    val ideaProject = fetchIdeaProjectForGradleProject(projectPath) ?: return description
    val currentAGPVersion = findFromBuildFiles(ideaProject)?.pluginVersion
    val recommendedAGPVersion = tryParseAndroidGradlePluginVersion(LatestKnownPluginVersionProvider.INSTANCE.get())
    description.addQuickFix("Install Build Tools $version and sync project",
                            InstallBuildToolsQuickFix(version, emptyList(), false))
    if (currentAGPVersion == null || recommendedAGPVersion == null || currentAGPVersion < recommendedAGPVersion) {
      description.addQuickFix(
        "Upgrade plugin to version ${GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())} and sync project",
        FixAndroidGradlePluginVersionQuickFix(null, null))
    }
    return description
  }
}