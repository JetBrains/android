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

import com.android.tools.idea.gradle.project.sync.extensions.findCauseMessage
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenStudioProxySettingsQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.util.io.toCanonicalPath
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.function.Consumer
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix

@JvmField val COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX = "Could not install Gradle distribution from "

class GradleDistributionInstallIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message =
      issueData.error.findCauseMessage { message?.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX) == true } ?: return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance()
      .collectFailure(issueData.projectRoot.toCanonicalPath(), GradleSyncFailure.GRADLE_DISTRIBUTION_INSTALL_ERROR)

    val buildIssueComposer = BuildIssueComposer(message)
    val rootCause = issueData.failure.rootCause
    val rootCauseClassName = rootCause.className ?: return null
    if (issueData.error != rootCause) {
      buildIssueComposer.addDescriptionOnNewLine("Reason: ${rootCause.className}: ${rootCause.message}")
      buildIssueComposer.startNewParagraph()
      if (
        rootCauseClassName.contains(UnknownHostException::class.java.name) || rootCauseClassName.contains(ConnectException::class.java.name)
      ) {
        buildIssueComposer.addQuickFix(
          "Please ensure ",
          "gradle distribution url",
          " is correct.",
          GradleWrapperSettingsOpenQuickFix(issueData.projectRoot.toCanonicalPath(), "distributionUrl"),
        )
        buildIssueComposer.addQuickFix(
          "If you are behind an HTTP proxy, please ",
          "configure the proxy settings",
          ".",
          OpenStudioProxySettingsQuickFix(),
        )
      }
      if (
        rootCauseClassName.contains(RuntimeException::class.java.name) &&
          rootCause.message?.startsWith("Could not create parent directory for lock file") == true
      ) {
        buildIssueComposer.addDescriptionOnNewLine(
          """
          Please ensure Android Studio can write to the specified Gradle wrapper distribution directory.
          You can also change Gradle home directory in Gradle Settings.
          """
            .trimIndent()
        )
        buildIssueComposer.startNewParagraph()
        buildIssueComposer.addQuickFix("Open Gradle Settings", UnsupportedGradleVersionIssueChecker.OpenGradleSettingsQuickFix())
        buildIssueComposer.addQuickFix(
          "Open Gradle wrapper settings",
          GradleWrapperSettingsOpenQuickFix(issueData.projectRoot.toCanonicalPath(), null),
        )
      }
    }

    return buildIssueComposer.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>,
  ): Boolean {
    return failureCause.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX)
  }
}
