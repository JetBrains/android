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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleJdkSettingsQuickfix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer

class DaemonContextMismatchIssueChecker : GradleIssueChecker {
  private val JAVA_HOME = "javaHome="
  private val ERROR_DAEMON = "The newly created daemon process has a different context than expected."
  private val JAVA_HOME_DIFFERENT = "Java home is different."

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    val messageLines = message.lines()
    if (messageLines[0].isBlank() ||
        !messageLines[0].contains(ERROR_DAEMON) || messageLines.size <= 3 || messageLines[2] != JAVA_HOME_DIFFERENT) return null

    val expectedAndActual = parseExpectedAndActualJavaHome(message) ?: return null
    if (expectedAndActual.isEmpty()) return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, GradleSyncFailure.DAEMON_CONTEXT_MISMATCH)
    return BuildIssueComposer(messageLines[2]).apply {
      addDescriptionOnNewLine(expectedAndActual)
      startNewParagraph()
      addDescriptionOnNewLine("Please configure the JDK to match the expected one.")
      startNewParagraph()
      addQuickFix("Open JDK Settings", OpenGradleJdkSettingsQuickfix())
    }.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    val failureLines = failureCause.lines()
    return failureLines[0].contains(ERROR_DAEMON) && failureLines.size > 3 && failureLines[2] == JAVA_HOME_DIFFERENT
  }

  private fun parseExpectedAndActualJavaHome(message: String): String? {
    var startIndex = message.indexOf(JAVA_HOME)
    var endIndex = message.indexOf(",", startIndex)
    if (endIndex == -1 || startIndex == -1 || endIndex < startIndex) return null
    val expected = message.substring(startIndex + JAVA_HOME.length, endIndex)
    if (expected.isNotEmpty()) {
      // Now get the actual value.
      startIndex = message.indexOf(JAVA_HOME, endIndex)
      endIndex = message.indexOf(",", startIndex)
      if (endIndex == -1 || startIndex == -1 || endIndex < startIndex) return null
      val actual = message.substring(startIndex + JAVA_HOME.length, endIndex)
      return buildString {
        append("Expecting: '${expected}'")
        if (actual.isNotEmpty()) {
          append(" but was: '${actual}'.")
        } else {
          append(".")
        }
      }
    }
    return null
  }
}