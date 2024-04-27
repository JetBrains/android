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
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoMatchingConfigurationSelectionIssueCheckerTest {
  private val issueChecker = NoMatchingConfigurationSelectionIssueChecker()

  @Test
  fun `consumeBuildOutputFailureMessage is false when pattern is not present`() {
    val testConsumer = TestMessageEventConsumer()
    val consumed = issueChecker.consumeBuildOutputFailureMessage(
      message = "This message should not be consumed",
      failureCause = "",
      stacktrace = null,
      location = null,
      parentEventId = "",
      testConsumer
    )
    assertFalse(consumed)
    assertEmpty(testConsumer.messageEvents)
  }

  @Test
  fun `consumeBuildOutputFailureMessage is true when pattern is present`() {
    val testConsumer = TestMessageEventConsumer()
    val consumed = issueChecker.consumeBuildOutputFailureMessage(
      message = createErrorMessage(agpVersion = "8.1.0", agpCompiledVersion = "11", gradleJdkVersion = "8"),
      failureCause = "",
      stacktrace = null,
      location = null,
      parentEventId = "",
      testConsumer
    )
    assertTrue(consumed)
    assertEmpty(testConsumer.messageEvents)
  }

  @Test
  fun `AGP needs 11 but project uses 8`() {
    verifyBuildIssue(agpVersion = "7.0.0", agpCompiledVersion = "11", gradleJdkVersion = "8")
    verifyBuildIssue(agpVersion = "7.1.0-alpha09", agpCompiledVersion = "11", gradleJdkVersion = "8")
    verifyBuildIssue(agpVersion = "7.0.1-beta01", agpCompiledVersion = "11", gradleJdkVersion = "8")
    verifyBuildIssue(agpVersion = "7.2.0-dev", agpCompiledVersion = "11", gradleJdkVersion = "8")
  }

  @Test
  fun `AGP needs 17 but project uses 11`() {
    verifyBuildIssue(agpVersion = "9.0.0", agpCompiledVersion = "17", gradleJdkVersion = "11")
    verifyBuildIssue(agpVersion = "9.1.0-alpha09", agpCompiledVersion = "17", gradleJdkVersion = "11")
    verifyBuildIssue(agpVersion = "9.0.1-beta01", agpCompiledVersion = "17", gradleJdkVersion = "11")
    verifyBuildIssue(agpVersion = "9.2.0-dev", agpCompiledVersion = "17", gradleJdkVersion = "11")
  }

  @Test
  fun `AGP needs 8 but project uses 7`() {
    verifyBuildIssue(agpVersion = "6.0.0", agpCompiledVersion = "8", gradleJdkVersion = "7")
    verifyBuildIssue(agpVersion = "6.1.0-alpha09", agpCompiledVersion = "8", gradleJdkVersion = "7")
    verifyBuildIssue(agpVersion = "6.0.1-beta01", agpCompiledVersion = "8", gradleJdkVersion = "7")
    verifyBuildIssue(agpVersion = "6.2.0-dev", agpCompiledVersion = "8", gradleJdkVersion = "7")
  }

  @Test
  fun `No pattern does not generate BuildIssue`() {
    val message = "This message should not be consumed"
    val issueData = GradleIssueData("projectFolderPath", Throwable(message), null, null)
    val issue = issueChecker.createBuildIssue(issueData)
    assertThat(issue).isNull()
  }

  private fun verifyBuildIssue(agpVersion: String, agpCompiledVersion: String, gradleJdkVersion: String) {
    val message = createErrorMessage(agpVersion, agpCompiledVersion, gradleJdkVersion)
    val issueData = GradleIssueData("projectFolderPath", Throwable(message), null, null)
    val issue = issueChecker.createBuildIssue(issueData)
    assertThat(issue).isNotNull()
    val expectedMessage = "This project is configured to use an older Gradle JVM that supports up to version $gradleJdkVersion but the " +
                          "current AGP requires a Gradle JVM that supports version $agpCompiledVersion."
    assertThat(issue!!.description).contains(expectedMessage)
    val quickFixes = issue.quickFixes
    assertThat(quickFixes).hasSize(2)
    assertThat(quickFixes[0]).isInstanceOf(SelectJdkFromFileSystemQuickFix::class.java)
    assertThat(quickFixes[1]).isInstanceOf(OpenLinkQuickFix::class.java)
  }

  private fun createErrorMessage(agpVersion: String, agpCompiledVersion: String, gradleJdkVersion: String) =
    "No matching variant of com.android.tools.build:gradle:$agpVersion was found. The consumer was configured to find a library for use " +
    "during runtime, compatible with Java $gradleJdkVersion, packaged as a jar, and its dependencies declared externally, as well as " +
    "attribute 'org.gradle.plugin.api-version' with value '8.0' but:\n" +
    "  - Variant 'apiElements' capability com.android.tools.build:gradle:$agpVersion declares a library, packaged as a jar, and its " +
    "dependencies declared externally:\n" +
    "      - Incompatible because this component declares a component for use during compile-time, compatible with Java " +
    "$agpCompiledVersion and the consumer needed a component for use during runtime, compatible with Java $gradleJdkVersion\n" +
    "      - Other compatible attribute:\n" +
    "          - Doesn't say anything about org.gradle.plugin.api-version (required '8.0')\n" +
    "  - Variant 'runtimeElements' capability com.android.tools.build:gradle:$agpVersion declares a library for use during runtime, " +
    "packaged as a jar, and its dependencies declared externally:\n" +
    "      - Incompatible because this component declares a component, compatible with Java $agpCompiledVersion and the consumer needed " +
    "a component, compatible with Java $gradleJdkVersion\n" +
    "      - Other compatible attribute:\n" +
    "          - Doesn't say anything about org.gradle.plugin.api-version (required '8.0')"
}