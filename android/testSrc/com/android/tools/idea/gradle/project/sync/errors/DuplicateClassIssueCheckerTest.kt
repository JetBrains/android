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

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.BuildException

import org.junit.Test

class DuplicateClassIssueCheckerTest {
  private val VALID_MESSAGE =
    "Duplicate class org.intellij.lang.annotations.Identifier found in modules jetified-annotations-12.0 (com.intellij:annotations:12.0) and jetified-annotations-13.0 (org.jetbrains:annotations:13.0)\n" +
    "\n" +
    "Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>."
  private val issueChecker = DuplicateClassIssueChecker()

  @Test
  fun `not BuildException causes null issue`() {
    val issueData = GradleIssueData("projectFolderPath", Throwable(VALID_MESSAGE), null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  @Test
  fun `BuildException with no RuntimeException causes null issue`() {
    val issueData = GradleIssueData("projectFolderPath", BuildException("BuildException", Throwable(VALID_MESSAGE)), null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  @Test
  fun `incorrect message causes null issue`() {
    val issueData = GradleIssueData("projectFolderPath", BuildException("BuildException", RuntimeException("Bad message")), null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  @Test
  fun `href is replaced with correct message`() {
    val issueData = GradleIssueData("projectFolderPath", BuildException("Build exception", RuntimeException(VALID_MESSAGE)), null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat(buildIssue.description).doesNotContain("\"<a href=\"d.android.com/r/tools/classpath-sync-errors\"")
    assertThat(buildIssue.description).contains("<a href=\"open.more.details\"")
  }

  @Test
  fun `testCheckIssueHandled`() {
    assertThat(
      issueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Duplicate class  ABC \n" +
        "Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>.",
        "Caused by: java.lang.RuntimeException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      issueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Duplicate class  ABC \n" +
        "Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>.",
        "Caused by: java.net.SocketException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(false)
  }
}