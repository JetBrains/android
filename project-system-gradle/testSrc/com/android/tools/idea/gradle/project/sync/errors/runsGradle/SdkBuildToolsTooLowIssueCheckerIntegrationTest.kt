/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.runsGradle

import com.android.tools.idea.gradle.project.sync.errors.SdkBuildToolsTooLowIssueChecker
import com.android.tools.idea.gradle.project.sync.quickFixes.InstallBuildToolsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class SdkBuildToolsTooLowIssueCheckerIntegrationTest: AndroidGradleTestCase() {
  private val sdkBuildToolsTooLowIssueChecker = SdkBuildToolsTooLowIssueChecker()

  fun testCheckIssue() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val error = "The SDK Build Tools revision (1.0.0) is too low for project ':app'. Minimum required is 2.0.3"
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(Throwable(error)), null, null)
    val buildIssue = sdkBuildToolsTooLowIssueChecker.check(issueData)

    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue!!.description).contains(error)
    Truth.assertThat(buildIssue.quickFixes).hasSize(2)
    Truth.assertThat(buildIssue.quickFixes[0]).isInstanceOf(InstallBuildToolsQuickFix::class.java)
    Truth.assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
  }
}