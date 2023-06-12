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
import com.android.tools.idea.gradle.project.sync.idea.issues.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.rendering.classloading.ClassConverter.classVersionToJdk
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer

/**
 * Issue checker to add a more precise message when AGP requires a newer version of Gradle JVM. It expects messages like this:
 *
 * "<class> has been compiled by a more recent version of the Java Runtime (class file version <JDK version used in AGP>), "
 * "this version of the Java Runtime only recognizes class file versions up to <Maximum version supported by Gradle JDK>"
 *
 * If the pattern is found, it will add the following quick fixes:
 *
 * - [SelectJdkFromFileSystemQuickFix] that will open the settings tab to configure Gradle JVM
 * - [OpenLinkQuickFix] with message "See AGP Release Notes..."
 */
@Suppress("UnstableApiUsage")
class JvmOldIssueChecker: GradleIssueChecker {
  companion object {
    private const val COMPILE_ERROR_OLD_TARGET = ".+ has been compiled by a more recent version of the Java Runtime \\(class file " +
                                                 "version (\\d+)\\.0\\), this version of the Java Runtime only recognizes class file " +
                                                 "versions up to (\\d+)\\.\\d+"
  }

  override fun check(issueData: GradleIssueData): BuildIssue? {
    // This method is a wrapper to [createBuildIssue] but here we also report to [UsageTracker] when the issue is created. The reason for
    // this separation is to be able to have unit tests that do not extend [AndroidGradleTestCase] or more complex test classes.
    val buildIssue = createBuildIssue(issueData)
    if (buildIssue != null) {
      // Log metrics.
      invokeLater {
        updateUsageTracker(issueData.projectPath, AndroidStudioEvent.GradleSyncFailure.GRADLE_JVM_NOT_COMPATIBLE_WITH_AGP)
      }
    }
    return buildIssue
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return Regex(COMPILE_ERROR_OLD_TARGET).find(message) != null
  }

  @VisibleForTesting
  fun createBuildIssue(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    val match = Regex(COMPILE_ERROR_OLD_TARGET).find(message) ?: return null
    val agpJdkVersion = classVersionToJdk(match.groups[1]!!.value.toInt())
    val maxSupportedVersion = classVersionToJdk(match.groups[2]!!.value.toInt())
    return BuildIssueComposer("Gradle JVM version incompatible.").apply {
      addDescription("This project is configured to use an older Gradle JVM that supports up to version $maxSupportedVersion but the " +
                     "current AGP requires a Gradle JVM that supports version $agpJdkVersion.")
      addQuickFix(SelectJdkFromFileSystemQuickFix())
      addQuickFix("See AGP Release Notes...", OpenLinkQuickFix("https://developer.android.com/studio/releases/gradle-plugin"))
    }.composeBuildIssue()
  }
}