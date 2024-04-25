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

import com.android.tools.idea.gradle.project.sync.errors.MissingAndroidSdkIssueChecker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class MissingAndroidSdkIssueCheckerIntegrationTest : AndroidGradleTestCase() {
  private val missingAndroidSdkIssueChecker = MissingAndroidSdkIssueChecker()

  fun testCheckIssue() {
    loadSimpleApplication()
    val issueData = GradleIssueData(
      projectFolderPath.path, RuntimeException("No sdk.dir property defined in local.properties file."), null,
      null
    )
    val buildIssue = missingAndroidSdkIssueChecker.check(issueData)

    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue!!.description).contains(
      "No sdk.dir property defined in local.properties file.\nPlease fix the 'sdk.dir' property in the local.properties file.")
    // Verify quickFix
    Truth.assertThat(buildIssue.quickFixes).hasSize(1)
    Truth.assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
  }
}