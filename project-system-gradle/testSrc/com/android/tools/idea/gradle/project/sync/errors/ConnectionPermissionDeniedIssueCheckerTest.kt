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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.net.SocketException

class ConnectionPermissionDeniedIssueCheckerTest : AndroidGradleTestCase() {
  private val connectionPermissionDeniedIssueChecker = ConnectionPermissionDeniedIssueChecker()

  fun testCheckIssue() {
    val cause = SocketException("Permission denied: connect")
    val issueData = GradleIssueData(projectFolderPath.path, cause, null,null)

    val buildIssue = connectionPermissionDeniedIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Connection to the Internet denied.")

    // Verify Quickfixes
    val quickFixes = buildIssue.quickFixes
    assertThat(quickFixes).hasSize(1)
    assertThat(quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      connectionPermissionDeniedIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Build failed with Exception: Permission denied: connect",
        "Caused by: java.net.SocketException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}