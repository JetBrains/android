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
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class GradleBrokenPipeIssueCheckerTest : AndroidGradleTestCase() {
  private val gradleBrokenPipeIssueChecker = GradleBrokenPipeIssueChecker()

  fun testCheckIssue() {
    val issueData = GradleIssueData(projectFolderPath.path, Throwable("Broken pipe"))
    val buildIssue = gradleBrokenPipeIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Broken pipe.\nThe Gradle daemon may be trying to use ipv4 instead of ipv6.")
    // Verify quickFixes.
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      gradleBrokenPipeIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Broken pipe",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}