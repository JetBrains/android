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

import com.android.tools.idea.gradle.project.sync.errors.InternetConnectionIssueChecker
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.settings.GradleSettings

class InternetConnectionIssueCheckerIntegrationTest : AndroidGradleTestCase() {
  private val internetConnectionIssueChecker = InternetConnectionIssueChecker()

  fun testCheckIssue() {
    loadSimpleApplication()
    GradleSettings.getInstance(project).isOfflineWork = true
    val issueData = GradleIssueData(project.basePath.toString(), Throwable("Network is unreachable"), null, null)
    val buildIssue = internetConnectionIssueChecker.check(issueData)

    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue!!.description).contains("Network is unreachable")
    // Check QuickFix.
    Truth.assertThat(buildIssue.quickFixes).hasSize(1)
    Truth.assertThat(buildIssue.quickFixes[0]).isInstanceOf(ToggleOfflineModeQuickFix::class.java)
  }
}