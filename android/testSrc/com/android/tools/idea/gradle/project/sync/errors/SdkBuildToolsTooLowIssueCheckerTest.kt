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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class SdkBuildToolsTooLowIssueCheckerTest: AndroidGradleTestCase() {
  private val sdkBuildToolsTooLowIssueChecker = SdkBuildToolsTooLowIssueChecker()

  fun testCheckIssue() {
    loadProject(SIMPLE_APPLICATION)
    val error = "The SDK Build Tools revision (1.0.0) is too low for project ':app'. Minimum required is 2.0.3"
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(Throwable(error)), null, null)
    val buildIssue = sdkBuildToolsTooLowIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(InstallBuildToolsQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      sdkBuildToolsTooLowIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "The SDK Build Tools revision (24.0.0) is too low for project 'test'. Minimum required is 29.2.0",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}