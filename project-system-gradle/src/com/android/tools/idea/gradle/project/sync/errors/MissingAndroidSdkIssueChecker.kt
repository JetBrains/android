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

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.io.File
import java.util.function.Consumer
import java.util.regex.Pattern

class MissingAndroidSdkIssueChecker : GradleIssueChecker {
  private val FIX_SDK_DIR_PROPERTY = "Please fix the 'sdk.dir' property in the local.properties file."
  private val SDK_DIR_PROPERTY_MISSING = "No sdk.dir property defined in local.properties file."
  private val SDK_NOT_FOUND_PATTERN = Pattern.compile("The SDK directory '(.*?)' does not exist.")
  private val RUNTIME_EXCEPTION_TRACE_PATTERN = Pattern.compile("Caused by: java.lang.RuntimeException(.*)")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (rootCause !is RuntimeException || message.isBlank() ||
        message != SDK_DIR_PROPERTY_MISSING && !SDK_NOT_FOUND_PATTERN.matcher(message).matches()) return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, GradleSyncFailure.SDK_NOT_FOUND)

    val propertiesFile = File(issueData.projectPath, FN_LOCAL_PROPERTIES)
    if (!propertiesFile.isFile) return null

    return BuildIssueComposer(message).apply {
      addDescriptionOnNewLine(FIX_SDK_DIR_PROPERTY)
      startNewParagraph()
      addQuickFix("Open local.properties File", OpenFileAtLocationQuickFix(FilePosition(propertiesFile, 0, 0)))
    }.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return stacktrace != null && RUNTIME_EXCEPTION_TRACE_PATTERN.matcher(stacktrace).find() &&
      (failureCause == SDK_DIR_PROPERTY_MISSING || SDK_NOT_FOUND_PATTERN.matcher(failureCause).matches())
  }
}