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

import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.errors.OldAndroidPluginIssueChecker.Companion.MINIMUM_AGP_VERSION_JDK_11
import com.android.tools.idea.gradle.project.sync.errors.OldAndroidPluginIssueChecker.Companion.MINIMUM_AGP_VERSION_JDK_8
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.UpgradeGradleVersionsQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests.overrideJdkTo8
import com.android.tools.idea.testing.AndroidGradleTests.restoreJdk
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.UnsupportedVersionException
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class OldAndroidPluginIssueCheckerTest: AndroidGradleTestCase() {
  private val oldAndroidPluginIssueChecker = OldAndroidPluginIssueChecker()

  fun testCheckIssueGradle() {
    verifyGradleIssue(MINIMUM_AGP_VERSION_JDK_11)
  }

  fun testCheckIssueGradleJdk8() {
    overrideJdkTo8()
    try {
      verifyGradleIssue(MINIMUM_AGP_VERSION_JDK_8)
    }
    finally {
      restoreJdk()
    }
  }

  fun verifyGradleIssue(minimumAgpVersion: AgpVersion) {
    val errMsg = "Support for builds using Gradle versions older than 2.6 was removed in tooling API version 5.0. You are currently using Gradle version 2.2. You should upgrade your Gradle build to use Gradle 2.6 or later."
    val expectedErrorMsg = "This version of Android Studio requires projects to use Gradle 4.10 or newer. This project is using Gradle 2.2."
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMsg, UnsupportedVersionException(errMsg)), null, null)
    val minimumGradleVersion = OldAndroidPluginIssueChecker.MINIMUM_GRADLE_VERSION
    val latestGradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION)
    val latestAgpVersion = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())

    val buildIssue = oldAndroidPluginIssueChecker.check(issueData)
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(expectedErrorMsg)
    assertThat(buildIssue.quickFixes).hasSize(3)

    assertThat(buildIssue.quickFixes[0]).isInstanceOf(UpgradeGradleVersionsQuickFix::class.java)
    val upgradeQuickFixMinimum = buildIssue.quickFixes[0] as UpgradeGradleVersionsQuickFix
    assertThat(upgradeQuickFixMinimum.agpVersion).isEqualTo(minimumAgpVersion)
    assertThat(upgradeQuickFixMinimum.gradleVersion).isEqualTo(minimumGradleVersion)

    assertThat(buildIssue.quickFixes[1]).isInstanceOf(UpgradeGradleVersionsQuickFix::class.java)
    val upgradeQuickFixLatest = buildIssue.quickFixes[1] as UpgradeGradleVersionsQuickFix
    assertThat(upgradeQuickFixLatest.agpVersion).isEqualTo(latestAgpVersion)
    assertThat(upgradeQuickFixLatest.gradleVersion).isEqualTo(latestGradleVersion)

    assertThat(buildIssue.quickFixes[2]).isInstanceOf(OpenPluginBuildFileQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      oldAndroidPluginIssueChecker.consumeBuildOutputFailureMessage(
        "Support for builds using Gradle versions older than 2.6 was removed in tooling API version 5.0. You are currently using Gradle version 2.2. You should upgrade your Gradle build to use Gradle 2.6 or later.",
        "Support for builds using Gradle versions older than 2.6 was removed in tooling API version 5.0. You are currently using Gradle version 2.2. You should upgrade your Gradle build to use Gradle 2.6 or later.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}