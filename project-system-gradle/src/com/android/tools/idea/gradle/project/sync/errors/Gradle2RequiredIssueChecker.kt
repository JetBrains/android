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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.quickFixes.CreateGradleWrapperQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer

class Gradle2RequiredIssueChecker : GradleIssueChecker {
  private val MESSAGE_SUFFIX = "org/codehaus/groovy/runtime/typehandling/ShortTypeHandling"

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (message.isBlank() || !message.endsWith(MESSAGE_SUFFIX)) return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, GradleSyncFailure.GRADLE2_REQUIRED)
    return BuildIssueComposer("Gradle ${SdkConstants.GRADLE_MINIMUM_VERSION} is required.").apply {
      addDescriptionOnNewLine(message)
      startNewParagraph()
      addQuickFix("Migrate to Gradle wrapper and sync project", CreateGradleWrapperQuickFix())
    }.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return failureCause.endsWith(MESSAGE_SUFFIX)
  }
}