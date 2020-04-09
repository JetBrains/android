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

import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.UnsupportedVersionException
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class UnsupportedGradleVersionIssueCheckerTest: AndroidGradleTestCase() {
  private val unsupportedGradleVersionIssueChecker = UnsupportedGradleVersionIssueChecker()

  fun testCheckIssue() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)

    val errMessage = "Gradle version 2.2 is required."
    val issueData = GradleIssueData(projectFolderPath.path, UnsupportedVersionException(errMessage), null, null)
    val buildIssue = unsupportedGradleVersionIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Gradle version 2.2 is required.\n\nPlease fix the project's Gradle settings.")
    assertThat(buildIssue.quickFixes).hasSize(3)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(UnsupportedGradleVersionIssueChecker.FixGradleVersionInWrapperQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
    assertThat(buildIssue.quickFixes[2]).isInstanceOf(UnsupportedGradleVersionIssueChecker.OpenGradleSettingsQuickFix::class.java)
  }

  fun testCheckIssueOneQuickFix() {
    // This is to check we still show one quickFix if we can't fetch the IDEA project for the current Gradle project.
    val errMessage = "Gradle version 2.2 is required."
    val issueData = GradleIssueData(projectFolderPath.path, UnsupportedVersionException(errMessage), null, null)
    val buildIssue = unsupportedGradleVersionIssueChecker.check(issueData)

    assertThat(buildIssue!!.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(UnsupportedGradleVersionIssueChecker.OpenGradleSettingsQuickFix::class.java)
  }

  fun testCheckIssueWithPlugin2_3AndGradleOlderThan3_3() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)

    val errMessage = "Minimum supported Gradle version is 3.3. Current version is 2.14.1. " +
                     "If using the gradle wrapper, try editing the distributionUrl in " +
                     "/MyApplication/gradle/wrapper/gradle-wrapper.properties to gradle-3.3-all.zip"
    val issueData = GradleIssueData(projectFolderPath.path, UnsupportedVersionException(errMessage), null, null)
    val buildIssue = unsupportedGradleVersionIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Minimum supported Gradle version is 3.3. Current version is 2.14.1.\n\n" +
                                                  "Please fix the project's Gradle settings.")
    assertThat(buildIssue.quickFixes).hasSize(3)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(UnsupportedGradleVersionIssueChecker.FixGradleVersionInWrapperQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
    assertThat(buildIssue.quickFixes[2]).isInstanceOf(UnsupportedGradleVersionIssueChecker.OpenGradleSettingsQuickFix::class.java)
  }
}