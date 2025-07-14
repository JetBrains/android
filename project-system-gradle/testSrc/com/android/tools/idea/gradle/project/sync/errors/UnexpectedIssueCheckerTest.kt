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

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.UnsupportedVersionException
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Rule
import org.junit.Test

class UnexpectedIssueCheckerTest {
  private val unexpectedIssueChecker = UnexpectedIssueChecker()

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun testCheckIssue() {
    val error = "This is an unexpected error. Please file a bug containing the idea.log file."
    val issueData = GradleIssueData(projectRule.project.basePath!!, UnsupportedVersionException(error), null, null)
    val buildIssue = unexpectedIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(FileBugQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(ShowLogQuickFix::class.java)
  }

  @Test
  fun testCheckIssueHandled() {
    assertThat(
      unexpectedIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "This is an unexpected error. Please file a bug containing the idea.log file.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}