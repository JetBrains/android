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
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class CorruptGradleDependencyIssueCheckerTest : AndroidGradleTestCase() {
  private val corruptGradleDependencyIssueChecker = CorruptGradleDependencyIssueChecker()

  fun testCheckIssue() {
    val cause = Throwable("Premature end of Content-Length delimited message body")
    val issueData = GradleIssueData(projectFolderPath.path, cause, null,null)

    val buildIssue = corruptGradleDependencyIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.title).contains("Gradle's dependency cache seems to be corrupt or out of sync.")

    // Verify Quickfixes
    val quickFixes = buildIssue.quickFixes
    assertThat(quickFixes).hasSize(1)
    assertThat(quickFixes[0]).isInstanceOf(ClassLoadingIssueChecker.SyncProjectQuickFix::class.java)
  }

}