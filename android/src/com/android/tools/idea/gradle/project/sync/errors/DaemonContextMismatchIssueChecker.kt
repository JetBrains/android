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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenProjectStructureQuickfix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler

class DaemonContextMismatchIssueChecker : GradleIssueChecker {
  private val JAVA_HOME = "javaHome="

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    val messageLines = message.lines()
    if (messageLines[0].isEmpty() ||
        !messageLines[0].contains("The newly created daemon process has a different context than expected.") ||
        messageLines.size <= 3 || messageLines[2] != "Java home is different.") return null

    val expectedAndActual = parseExpectedAndActualJavaHome(message) ?: return null
    if (expectedAndActual.isNotEmpty()) {
      // Log metrics.
      invokeLater {
        updateUsageTracker(issueData.projectPath, GradleSyncFailure.DAEMON_CONTEXT_MISMATCH)
      }
      val description = MessageComposer(messageLines[2]).apply {
        addDescription(expectedAndActual)
        addDescription("Please configure the JDK to match the expected one.")
        addQuickFix("Open JDK Settings", OpenProjectStructureQuickfix())
      }
      return object : BuildIssue {
        override val title = "Gradle Sync Issues."
        override val description = description.buildMessage()
        override val quickFixes = description.quickFixes
        override fun getNavigatable(project: Project) = null
      }
    }
    return null
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