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
package com.android.tools.idea.gradle.project.sync.errors.runsGradleErrors

import com.android.tools.idea.gradle.project.sync.errors.InternetConnectionIssueChecker
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.Rule
import org.junit.Test

class InternetConnectionIssueCheckerIntegrationTest {
  private val internetConnectionIssueChecker = InternetConnectionIssueChecker()

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun testCheckIssue() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    GradleSettings.getInstance(projectRule.project).isOfflineWork = true
    val issueData = GradleIssueData(projectRule.project.basePath!!, Throwable("Network is unreachable"), null, null)
    val buildIssue = internetConnectionIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Network is unreachable")
    // Check QuickFix.
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat((buildIssue.quickFixes.first() as ToggleOfflineModeQuickFix).enableOfflineMode).isFalse()
  }
}