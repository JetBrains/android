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

import com.android.SdkConstants
import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.build.FilePosition
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.io.File

class GradleDslMethodNotFoundIssueCheckerTest : AndroidGradleTestCase() {
  private val gradleDslMethodNotFoundIssueChecker = GradleDslMethodNotFoundIssueChecker()

  fun testCheckIssueWithMethodNotFoundInSettingsFile() {
    loadSimpleApplication()

    val settingsFile = File(project.basePath, FN_SETTINGS_GRADLE)
    val issueData = GradleIssueData(projectFolderPath.path, Throwable("Gradle DSL method not found. \nCould not find method abdd()"),
                                    null, FilePosition(settingsFile, 2, 0))
    val buildIssue = gradleDslMethodNotFoundIssueChecker.check(issueData)

    // Check error handled correctly.
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Gradle DSL method not found")
    // Verify quickFixes.
    assertThat(buildIssue.quickFixes).hasSize(1)
    val quickFix = buildIssue.quickFixes[0]
    assertThat(quickFix).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
    assertThat((quickFix as OpenFileAtLocationQuickFix).myFilePosition.file.path).isEqualTo(settingsFile.path)
  }

  fun testCheckIssueWithMethodNotFoundInBuildFile() {
    loadSimpleApplication()

    val buildFile = File(project.basePath, SdkConstants.FN_BUILD_GRADLE)
    val issueData = GradleIssueData(projectFolderPath.path, Throwable("Gradle DSL method not found. \nCould not find method abdd()"),
                                    null, FilePosition(buildFile, 0, 0))
    val buildIssue = gradleDslMethodNotFoundIssueChecker.check(issueData)

    // Check error handled correctly.
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Your project may be using a version of the Android Gradle plug-in that does not contain the " +
                                                  "method (e.g. 'testCompile' was added in 1.1.0).")
    // Verify quickFixes.
    assertThat(buildIssue.quickFixes).hasSize(3)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(FixAndroidGradlePluginVersionQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(GradleDslMethodNotFoundIssueChecker.GetGradleSettingsQuickFix::class.java)
    assertThat(buildIssue.quickFixes[2]).isInstanceOf(GradleDslMethodNotFoundIssueChecker.ApplyGradlePluginQuickFix::class.java)
  }
}