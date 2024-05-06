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

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.google.common.truth.Truth
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Test

@Suppress("UnstableApiUsage")
class UnsupportedJavaVersionForAgpIssueCheckerTest {
  private val issueChecker = UnsupportedJavaVersionForAgpIssueChecker()

  @Test
  fun `consumeBuildOutputFailureMessage is false when pattern is not present`() {
    val testConsumer = TestMessageEventConsumer()
    val consumed = issueChecker.consumeBuildOutputFailureMessage(
      message = "This message should not be consumed",
      failureCause = "Cause is none",
      stacktrace = null,
      location = null,
      parentEventId = "",
      testConsumer
    )
    Truth.assertThat(consumed).isFalse()
    Truth.assertThat(testConsumer.messageEvents).isEmpty()
  }

  @Test
  fun `consumeBuildOutputFailureMessage is true when pattern is present`() {
    val testConsumer = TestMessageEventConsumer()
    val consumed = issueChecker.consumeBuildOutputFailureMessage(
      message = createErrorMessage(agpMinimumJdkVersion = "17", gradleJdkVersion = "11"),
      failureCause = "",
      stacktrace = null,
      location = null,
      parentEventId = "",
      testConsumer
    )
    Truth.assertThat(consumed).isTrue()
    Truth.assertThat(testConsumer.messageEvents).isEmpty()
  }

  @Test
  fun `AGP needs 17 but project uses 11`() {
    verifyBuildIssue(agpMinimumJdkVersion = "17", gradleJdkVersion = "11")
  }

  @Test
  fun `AGP needs 17 but project uses 8`() {
    verifyBuildIssue(agpMinimumJdkVersion = "17", gradleJdkVersion = "1.8")
  }

  @Test
  fun `AGP needs 11 but project uses 8`() {
    verifyBuildIssue(agpMinimumJdkVersion = "11", gradleJdkVersion = "1.8")
  }

  @Test
  fun `No pattern does not generate BuildIssue`() {
    val message = "This message should not be consumed"
    val issueData = GradleIssueData("projectFolderPath", Throwable(message), null, null)
    val issue = issueChecker.createBuildIssue(issueData)
    Truth.assertThat(issue).isNull()
  }

  private fun verifyBuildIssue(agpMinimumJdkVersion: String, gradleJdkVersion: String) {
    val message = createErrorMessage(agpMinimumJdkVersion, gradleJdkVersion)
    val issueData = GradleIssueData("projectFolderPath", Throwable(message), null, null)
    val issue = issueChecker.createBuildIssue(issueData)
    Truth.assertThat(issue).isNotNull()
    val expectedMessage = "This project is configured to use an older Gradle JVM that supports up to version $gradleJdkVersion but the " +
                          "current AGP requires a Gradle JVM that supports version $agpMinimumJdkVersion."
    Truth.assertThat(issue!!.description).contains(expectedMessage)
    val quickFixes = issue.quickFixes
    Truth.assertThat(quickFixes).hasSize(2)
    Truth.assertThat(quickFixes[0]).isInstanceOf(SelectJdkFromFileSystemQuickFix::class.java)
    Truth.assertThat(quickFixes[1]).isInstanceOf(OpenLinkQuickFix::class.java)
  }

  private fun createErrorMessage(agpMinimumJdkVersion: String, gradleJdkVersion: String) =
    "Build file 'build.gradle' line: 2\n" +
    "\n" +
    "An exception occurred applying plugin request [id: 'com.android.application']\n" +
    "> Failed to apply plugin 'com.android.internal.application'.\n" +
    "   > Android Gradle plugin requires Java $agpMinimumJdkVersion to run. You are currently using Java $gradleJdkVersion.\n" +
    "      Your current JDK is located in /Users/vmadalin/Develop/studio-main/prebuilts/studio/jdk/jdk11/mac-arm64/Contents/Home\n" +
    "      You can try some of the following options:\n" +
    "       - changing the IDE settings.\n" +
    "       - changing the JAVA_HOME environment variable.\n" +
    "       - changing `org.gradle.java.home` in `gradle.properties`."
}