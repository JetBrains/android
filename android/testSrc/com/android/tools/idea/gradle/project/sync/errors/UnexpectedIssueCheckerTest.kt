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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.UnsupportedVersionException
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class UnexpectedIssueCheckerTest: AndroidGradleTestCase() {
  private val unexpectedIssueChecker = UnexpectedIssueChecker()

  fun testCheckIssue() {
    val error = "This is an unexpected error. Please file a bug containing the idea.log file."
    val issueData = GradleIssueData(projectFolderPath.path, UnsupportedVersionException(error), null, null)
    val buildIssue = unexpectedIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(error)
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(UnexpectedIssueChecker.FileBugQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(UnexpectedIssueChecker.ShowLogQuickFix::class.java)
  }
}