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
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler

/**
 * These String constants are being used in [GradleNotificationExtension] to add
 * "quick-fix"/"help" hyperlinks to error messages. Given that the contract between the consumer and producer of error messages is pretty
 * loose, please do not use these constants, to prevent any unexpected side effects during project sync.
 */
const val UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX = "The project is using an unsupported version of the Android Gradle plug-in"
const val READ_MIGRATION_GUIDE_MSG = "Please read the migration guide"

class UnsupportedModelVersionIssueChecker: GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (message.isBlank() || !message.startsWith(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) return null

    // Log metrics.
    invokeLater {
      SyncErrorHandler.updateUsageTracker(issueData.projectPath, GradleSyncFailure.UNSUPPORTED_MODEL_VERSION)
    }
    val description = MessageComposer(message).apply {
      addQuickFix(
        "Upgrade plugin to version ${GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())} and sync project",
        FixAndroidGradlePluginVersionQuickFix(null, null))
    }
    return object : BuildIssue {
      override val title = "Gradle Sync issues."
      override val description = message
      override val quickFixes = description.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }
}