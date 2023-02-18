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
import com.android.tools.idea.gradle.project.sync.idea.issues.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Test

@Suppress("UnstableApiUsage")
class JvmOldIssueCheckerTest {
  private val issueChecker = JvmOldIssueChecker()

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
    assertThat(consumed).isFalse()
    assertThat(testConsumer.messageEvents).isEmpty()
  }

  @Test
  fun `consumeBuildOutputFailureMessage is true when pattern is present`() {
    val testConsumer = TestMessageEventConsumer()
    val consumed = issueChecker.consumeBuildOutputFailureMessage(
      message = createErrorMessage(agpCompiled = "61.0", gradleRuntime = "55.0"),
      failureCause = "com/android/tools/lint/model/LintModelSeverity has been compiled by a more recent version of the Java Runtime " +
                     "(class file version 61.0), this version of the Java Runtime only recognizes class file versions up to 55.0",
      stacktrace = null,
      location = null,
      parentEventId = "",
      testConsumer
    )
    assertThat(consumed).isTrue()
    assertThat(testConsumer.messageEvents).isEmpty()
  }

  @Test
  fun `AGP needs 17 but project uses 11`() {
    verifyBuildIssue(agpCompiled = "61.0", gradleRuntime = "55.0", resolvedAgp = "17", resolvedGradle = "11")
  }

  @Test
  fun `AGP needs 17 but project uses 8`() {
    verifyBuildIssue(agpCompiled = "61.0", gradleRuntime = "52.0", resolvedAgp = "17", resolvedGradle = "1.8")
  }

  @Test
  fun `AGP needs 11 but project uses 8`() {
    verifyBuildIssue(agpCompiled = "55.0", gradleRuntime = "52.0", resolvedAgp = "11", resolvedGradle = "1.8")
  }

  @Test
  fun `No pattern does not generate BuildIssue`() {
    val message = "This message should not be consumed"
    val issueData = GradleIssueData("projectFolderPath", Throwable(message), null, null)
    val issue = issueChecker.createBuildIssue(issueData)
    assertThat(issue).isNull()
  }

  private fun verifyBuildIssue(agpCompiled: String, gradleRuntime: String, resolvedAgp: String, resolvedGradle: String) {
    val message = createErrorMessage(agpCompiled, gradleRuntime)
    val issueData = GradleIssueData("projectFolderPath", Throwable(message), null, null)
    val issue = issueChecker.createBuildIssue(issueData)
    assertThat(issue).isNotNull()
    val expectedMessage = "This project is configured to use an older Gradle JVM that supports up to version $resolvedGradle but the " +
                          "current AGP requires a Gradle JVM that supports version $resolvedAgp."
    assertThat(issue!!.description).contains(expectedMessage)
    val quickFixes = issue.quickFixes
    assertThat(quickFixes).hasSize(2)
    assertThat(quickFixes[0]).isInstanceOf(SelectJdkFromFileSystemQuickFix::class.java)
    assertThat(quickFixes[1]).isInstanceOf(OpenLinkQuickFix::class.java)
  }

  private fun createErrorMessage(agpCompiled: String, gradleRuntime: String) =
    "Build file 'build.gradle' line: 2\n" +
    "\n" +
    "An exception occurred applying plugin request [id: 'com.android.application']\n" +
    "> Failed to apply plugin 'com.android.internal.application'.\n" +
    "   > Could not create an instance of type com.android.build.gradle.internal.dsl.ApplicationExtensionImpl\$AgpDecorated.\n" +
    "      > Could not create an instance of type com.android.build.gradle.internal.dsl.LintImpl\$AgpDecorated.\n" +
    "         > Could not generate a decorated class for type LintImpl\$AgpDecorated.\n" +
    "            > com/android/tools/lint/model/LintModelSeverity has been compiled by a more recent version of the Java " +
    "Runtime (class file version $agpCompiled), this version of the Java Runtime only recognizes class file versions up to " +
    "$gradleRuntime\n"
}