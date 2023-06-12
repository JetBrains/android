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

import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.Version
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AgpVersionNotSupportedIssueCheckerTest {
  private val agpVersionNotSupportedIssueChecker = AgpVersionNotSupportedIssueChecker()

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testCheckIssue() {
    val expectedNotificationMessage = "The project is using an incompatible version (AGP 3.1.4) of the Android Gradle plugin."
    val error = "The project is using an incompatible version (AGP 3.1.4) of the Android Gradle plugin. " +
                "Minimum supported version is AGP $GRADLE_PLUGIN_MINIMUM_VERSION."

    val issueData = GradleIssueData(":", Throwable(error), null, null)
    val buildIssue = agpVersionNotSupportedIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.description).contains(expectedNotificationMessage)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat((buildIssue.quickFixes[0] as OpenLinkQuickFix).link)
      .isEqualTo("https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility")
  }

  @Test
  fun testCheckIssueTooNew() {
    val expectedNotificationMessage = "The project is using an incompatible version (AGP 99.1.4) of the Android Gradle plugin."
    val error = "The project is using an incompatible version (AGP 99.1.4) of the Android Gradle plugin. " +
                "Latest supported version is AGP ${Version.ANDROID_GRADLE_PLUGIN_VERSION}."

    val issueData = GradleIssueData(":", Throwable(error), null, null)
    val buildIssue = agpVersionNotSupportedIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.description).contains(expectedNotificationMessage)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat((buildIssue.quickFixes[0] as OpenLinkQuickFix).link)
      .isEqualTo("https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility")
  }

  @Test
  fun testCheckIssueOldPreview() {
    val expectedNotificationMessage = "The project is using an incompatible version (AGP 3.1.0-alpha01) of the Android Gradle plugin."
    val error = "The project is using an incompatible version (AGP 3.1.0-alpha01) of the Android Gradle plugin. " +
                "Minimum supported version is AGP $GRADLE_PLUGIN_MINIMUM_VERSION."

    val issueData = GradleIssueData(":", Throwable(error), null, null)
    val buildIssue = agpVersionNotSupportedIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.description).contains(expectedNotificationMessage)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat((buildIssue.quickFixes[0] as OpenLinkQuickFix).link)
      .isEqualTo("https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility")
  }

  @Test
  fun testCheckIssueIncompatiblePreview() {
    val latestKnown = AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    val expectedNotificationMessage =
      "The project is using an incompatible preview version (AGP 7.1.0-beta01) of the Android Gradle plugin."
    val error = "The project is using an incompatible preview version (AGP 7.1.0-beta01) of the Android Gradle plugin. " +
                "Current compatible ${if (latestKnown.isPreview) "preview " else ""}version is AGP $latestKnown."
    val issueData = GradleIssueData(":", Throwable(error), null, null)
    val buildIssue = agpVersionNotSupportedIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.description).contains(expectedNotificationMessage)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat((buildIssue.quickFixes[0] as OpenLinkQuickFix).link)
      .isEqualTo("https://developer.android.com/studio/preview/features#agp-previews")
  }

  @Test
  fun testIssueHandled() {
    assertThat(
      agpVersionNotSupportedIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "The project is using an incompatible version (AGP 3.1.0) of the Android Gradle plugin. " +
        "Minimum supported version is AGP $GRADLE_PLUGIN_MINIMUM_VERSION.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }

  @Test
  fun testCheckIssueTooNewHandled() {
    assertThat(
      agpVersionNotSupportedIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "The project is using an incompatible version (AGP 99.0.0) of the Android Gradle plugin. " +
        "Latest supported version is AGP ${Version.ANDROID_GRADLE_PLUGIN_VERSION}.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )
    ).isTrue()
  }

  @Test
  fun testCheckIssueIncompatiblePreviewHandled() {
    val latestKnown = AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    assertThat(
      agpVersionNotSupportedIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "The project is using an incompatible preview version (AGP 4.1.0-alpha01) of the Android Gradle plugin. " +
        "Current compatible ${if (latestKnown.isPreview) "preview " else ""} version is AGP $latestKnown.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )
    ).isTrue()
  }

  @Test
  fun testIssueNotHandled() {
    assertThat(
      agpVersionNotSupportedIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "The project is using an incompatible version (AGP 0.9.0) of the Android Gradle Plugin. " +
        "Minimum supported version is AGP 1.0.0.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(false)
  }
}