/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer

/**
 * A [GradleIssueChecker] class used as base for related errors regarding runtime Java compiled version, parsing different expected
 * exceptions to add more precise message when AGP requires a newer version of Gradle JVM. The result message:
 *
 * Gradle JVM version incompatible.
 * This project is configured to use an older Gradle JVM that supports up to version X but
 * the current AGP requires a Gradle JVM that supports version Y.
 * - [SelectJdkFromFileSystemQuickFix] that will open the settings tab to configure Gradle JVM
 * - [OpenLinkQuickFix] with message "See AGP Release Notes..."
 */
@Suppress("UnstableApiUsage")
abstract class RuntimeJavaCompiledVersionIssueChecker : GradleIssueChecker {

  abstract val expectedErrorRegex: Regex
  abstract fun parseErrorRegexMatch(matchResult: MatchResult): Pair<String, String>?

  override fun check(issueData: GradleIssueData): BuildIssue? {
    // This method is a wrapper to [createBuildIssue] but here we also report to [UsageTracker] when the issue is created. The reason for
    // this separation is to be able to have unit tests that do not extend [AndroidGradleTestCase] or more complex test classes.
    val buildIssue = createBuildIssue(issueData)
    if (buildIssue != null) {
      // Log metrics.
      SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP)
    }
    return buildIssue
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
  ) = expectedErrorRegex.find(message) != null

  @VisibleForTesting
  fun createBuildIssue(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    val match = expectedErrorRegex.find(message) ?: return null
    return parseErrorRegexMatch(match)?.let { (agpMinCompatibleJdkVersion, gradleJdkVersion) ->
      createJdkVersionIncompatibleBuildIssue(agpMinCompatibleJdkVersion, gradleJdkVersion)
    }
  }

  private fun createJdkVersionIncompatibleBuildIssue(agpMinCompatibleJdkVersion: String, gradleJdkVersion: String) =
    BuildIssueComposer("Gradle JVM version incompatible.").apply {
      addDescriptionOnNewLine("This project is configured to use an older Gradle JVM that supports up to version $gradleJdkVersion but the " +
                                         "current AGP requires a Gradle JVM that supports version $agpMinCompatibleJdkVersion.")
      startNewParagraph()
      addQuickFix(SelectJdkFromFileSystemQuickFix())
      addQuickFix("See AGP Release Notes...", OpenLinkQuickFix("https://developer.android.com/studio/releases/gradle-plugin"))
    }.composeBuildIssue()
}