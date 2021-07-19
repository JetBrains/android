/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.quickFixes.DownloadAndroidStudioQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer
import java.util.regex.Pattern

/**
IssueChecker to handle the deprecation of AGP 3.1 in Android Studio BumbleBee and above.
 */
class AgpVersionNotSupportedIssueChecker: GradleIssueChecker {
  private val AGP_VERSION_NOT_SUPPORTED_PATTERN = Pattern.compile(
    "The project is using an incompatible version \\(AGP (.+)\\) of the Android Gradle plugin\\. Minimum supported version is AGP 3\\.2\\."
  )

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: ""
    if (message.isBlank() || !AGP_VERSION_NOT_SUPPORTED_PATTERN.matcher(message).matches()) return null

    logMetrics(issueData.projectPath)

    return BuildIssueComposer(message).apply {
      addQuickFix(
        "See Android Studio & AGP compatibility options.",
        OpenLinkQuickFix("https://android.devsite.corp.google.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility")
      )
    }.composeBuildIssue()
  }

  private fun logMetrics(issueDataProjectPath: String) {
      invokeLater {
        updateUsageTracker(issueDataProjectPath, AndroidStudioEvent.GradleSyncFailure.OLD_ANDROID_PLUGIN)
      }
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
  ): Boolean {
    return failureCause.contains("The project is using an incompatible version") &&
           failureCause.contains("Minimum supported version is AGP 3.2.")
  }
}