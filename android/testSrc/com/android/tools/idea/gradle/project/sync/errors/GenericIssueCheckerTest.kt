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
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.build.FilePosition
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.io.File

class GenericIssueCheckerTest: AndroidGradleTestCase() {
  private val genericIssueChecker = GenericIssueChecker()

  fun testCheckIssueWithLocation() {
    val errMessage = "Some error message.\nBuild file '/xyz/build.gradle' line: 3"
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMessage), null, null)
    val buildIssue = genericIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(errMessage)
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
  }

  fun testCheckIssueWithoutLocationInMessage() {
    val errMessage = "Some error message."
    val issueData = GradleIssueData(
      projectFolderPath.path,
      Throwable(errMessage),
      null,
      FilePosition(File(project.basePath, SdkConstants.FN_BUILD_GRADLE), 3, -1)
    )
    val buildIssue = genericIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(errMessage)
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
  }
}