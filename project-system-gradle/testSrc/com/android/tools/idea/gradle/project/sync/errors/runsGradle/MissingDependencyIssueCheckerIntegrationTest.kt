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

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.errors.MissingDependencyIssueChecker
import com.android.tools.idea.gradle.project.sync.errors.SearchInBuildFilesQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.settings.GradleSettings

class MissingDependencyIssueCheckerIntegrationTest: AndroidGradleTestCase() {
  private val missingDependencyIssueChecker = MissingDependencyIssueChecker()

  fun testCheckIssueWithoutLocation() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    GradleSettings.getInstance(project).isOfflineWork = true

    val errMessage = "Could not find any version that matches 1.0.0."
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMessage), null, null)
    val buildIssue = missingDependencyIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(errMessage)
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(ToggleOfflineModeQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(SearchInBuildFilesQuickFix::class.java)
  }

  fun testCheckIssueWithBuildFileLocation() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    GradleSettings.getInstance(project).isOfflineWork = true

    val errMessage = "Could not find myLib.\nRequired by: app\nBuild file '/xyz/build.gradle' line: 3"
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMessage), null, null)
    val buildIssue = missingDependencyIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(errMessage)
    assertThat(buildIssue.quickFixes).hasSize(3)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(ToggleOfflineModeQuickFix::class.java)
    assertThat(buildIssue.quickFixes[2]).isInstanceOf(SearchInBuildFilesQuickFix::class.java)
  }
}