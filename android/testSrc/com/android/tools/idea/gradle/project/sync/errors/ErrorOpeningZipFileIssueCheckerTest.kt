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
import com.android.tools.idea.gradle.project.sync.quickFixes.SyncProjectRefreshingDependenciesQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class ErrorOpeningZipFileIssueCheckerTest : AndroidGradleTestCase() {
  private val errorOpeningZipFileIssueChecker = ErrorOpeningZipFileIssueChecker()

  fun testCheckIssue() {
    val issueData = GradleIssueData(projectFolderPath.path, Throwable("error in opening zip file"), null, null)
    val buildIssue = errorOpeningZipFileIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Failed to open zip file.\n" +
                                                  "Gradle's dependency cache may be corrupt (this sometimes occurs after a network " +
                                                  "connection timeout.)")
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(SyncProjectRefreshingDependenciesQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      errorOpeningZipFileIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Build failed with Exception: error in opening zip file",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}