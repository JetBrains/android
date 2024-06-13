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

import com.android.tools.idea.gradle.project.sync.errors.InstallPlatformQuickFix
import com.android.tools.idea.gradle.project.sync.errors.MissingPlatformIssueChecker
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class MissingPlatformIssueCheckerIntegrationTest : AndroidGradleTestCase() {
  private val missingPlatformIssueChecker = MissingPlatformIssueChecker()

  fun testCheckIssue() {
    loadSimpleApplication()

    val issueDate = GradleIssueData(projectFolderPath.path, IllegalStateException("Failed to find target android-23"), null, null)

    val buildIssue = missingPlatformIssueChecker.check(issueDate)
    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue!!.description).contains("Failed to find target android-23")
    Truth.assertThat(buildIssue.description).contains("Install missing platform(s) and sync project")
    Truth.assertThat(buildIssue.quickFixes).hasSize(1)
    Truth.assertThat(buildIssue.quickFixes[0]).isInstanceOf(InstallPlatformQuickFix::class.java)
  }
}