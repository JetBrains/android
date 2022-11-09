/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpgradeGradleVersionsQuickFix
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.android.tools.idea.sdk.IdeSdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.projectRoots.JavaSdkVersion
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer
import java.util.regex.Pattern

class OldAndroidPluginIssueChecker: GradleIssueChecker {
  companion object {
    private val UNSUPPORTED_GRADLE_VERSION_PATTERN = Pattern.compile(
      "Support for builds using Gradle versions older than (.*?) .* You are currently using Gradle version (.*?). .*", Pattern.DOTALL)
    val MINIMUM_AGP_VERSION_JDK_8 = AgpVersion(3, 1, 0)
    val MINIMUM_AGP_VERSION_JDK_11 = AgpVersion(3, 2, 0)
    val MINIMUM_GRADLE_VERSION: GradleVersion = GradleVersion.version(SdkConstants.GRADLE_MINIMUM_VERSION)
  }


  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (message.isBlank()) return null
    var parsedMessage: String? = null
    var withMinimumVersion = false
    if (rootCause is UnsupportedVersionException) {
      parsedMessage = tryToGetUnsupportedGradleMessage(message)
      withMinimumVersion = true
    }
    if (parsedMessage == null) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION)
    }
    val composer = BuildIssueComposer(parsedMessage)
    if (withMinimumVersion) {
      val jdk = IdeSdks.getInstance().jdk
      val jdkVersion = if (jdk != null && jdk.versionString != null) JavaSdkVersion.fromVersionString(jdk.versionString!!) else null
      val isJdk8OrOlder = (jdkVersion != null) && (jdkVersion <= JavaSdkVersion.JDK_1_8)
      val minAgpToUse = if (isJdk8OrOlder) MINIMUM_AGP_VERSION_JDK_8 else MINIMUM_AGP_VERSION_JDK_11
      composer.addQuickFix(UpgradeGradleVersionsQuickFix(getCompatibleGradleVersion(minAgpToUse).version, minAgpToUse, "minimum"))
    }
    composer.addQuickFix("Open build file", OpenPluginBuildFileQuickFix())
    return composer.composeBuildIssue()
  }

  private fun tryToGetUnsupportedGradleMessage(message: String): String? {
    // Try to prevent updates to versions lower than 4.8.1 since they are not supported by AS Flamingo+
    val matcher = UNSUPPORTED_GRADLE_VERSION_PATTERN.matcher(message)
    if (matcher.matches()) {
      val minimumVersion = runCatching { GradleVersion.version(matcher.group(1)) }.getOrNull() ?: return null
      val usedVersion = runCatching { GradleVersion.version(matcher.group(2)) }.getOrNull() ?: return null
      if (minimumVersion <= MINIMUM_GRADLE_VERSION) {
        return "This version of Android Studio requires projects to use Gradle ${MINIMUM_GRADLE_VERSION.version}" +
               " or newer. This project is using Gradle ${usedVersion.version}."
      }
    }
    return null
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return tryToGetUnsupportedGradleMessage(failureCause) != null
  }
}