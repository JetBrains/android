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
import com.android.tools.idea.gradle.project.sync.quickFixes.InstallBuildToolsQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class MissingBuildToolsIssueCheckerTest: AndroidGradleTestCase() {
  private val missingBuildToolsIssueChecker = MissingBuildToolsIssueChecker()

  fun testCheckIssue() {
    loadProject(SIMPLE_APPLICATION)
    val errMsg = "Failed to find Build Tools revision 24.0.0 rc4"
    val issueData = GradleIssueData(projectFolderPath.path, IllegalStateException(errMsg), null, null)
    val buildIssue = missingBuildToolsIssueChecker.check(issueData)

     assertThat(buildIssue).isNotNull()
     assertThat(buildIssue!!.description).contains(errMsg)
     assertThat(buildIssue.quickFixes).hasSize(1)
     assertThat(buildIssue.quickFixes[0]).isInstanceOf(InstallBuildToolsQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      missingBuildToolsIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Cause: failed to find Build Tools revision ",
        "Caused by: java.lang.IllegalStateException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingBuildToolsIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Cause: Failed to find Build Tools revision ",
        "Caused by: com.intellij.openapi.externalSystem.model.ExternalSystemException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingBuildToolsIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Cause: failed to find Build Tools revision ",
        "Caused by: java.net.SocketException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(false)
  }
}