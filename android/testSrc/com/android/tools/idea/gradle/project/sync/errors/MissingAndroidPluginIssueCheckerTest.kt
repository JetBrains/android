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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.TestDataProvider
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class MissingAndroidPluginIssueCheckerTest : AndroidGradleTestCase() {
  private val missingAndroidPluginIssueChecker = MissingAndroidPluginIssueChecker()

  fun testCheckIssue() {
    val issueData = GradleIssueData(projectFolderPath.path, Throwable("Could not find com.android.tools.build:gradle:"), null, null)
    val buildIssue = missingAndroidPluginIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Add google Maven repository and sync project")
    assertThat(buildIssue.description).contains("Open File")
    // Verify quickFixes
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(AddGoogleMavenRepositoryQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenPluginBuildFileQuickFix::class.java)

    buildIssue.quickFixes[0].runQuickFix(project, TestDataProvider(project) as DataContext)
  }

  fun testCheckIssueHandled() {
    assertThat(
      missingAndroidPluginIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Could not find com.android.tools.build:gradle:",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}