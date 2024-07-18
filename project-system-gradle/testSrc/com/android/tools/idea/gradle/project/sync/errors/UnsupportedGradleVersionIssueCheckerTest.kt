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
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.UnsupportedVersionException
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class UnsupportedGradleVersionIssueCheckerTest : AndroidGradleTestCase() {
  private val unsupportedGradleVersionIssueChecker = UnsupportedGradleVersionIssueChecker()

  fun testCheckIssueOneQuickFix() {
    // This is to check we still show one quickFix if we can't fetch the IDEA project for the current Gradle project.
    val errMessage = "Minimum supported Gradle version is (6.3). Current version is 4.3"
    val issueData = GradleIssueData(projectFolderPath.path, UnsupportedVersionException(errMessage), null, null)
    val buildIssue = unsupportedGradleVersionIssueChecker.check(issueData)

    assertThat(buildIssue!!.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(UnsupportedGradleVersionIssueChecker.OpenGradleSettingsQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      unsupportedGradleVersionIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Minimum supported Gradle version is (6.3). Current version is 4.3",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}