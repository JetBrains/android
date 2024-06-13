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

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.errors.UnknownHostIssueChecker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.net.UnknownHostException

class UnknownHostIssueCheckerIntegrationTest: AndroidGradleTestCase() {
  private val unknownHostIssueChecker = UnknownHostIssueChecker()

  fun testCheckIssue() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    GradleSettings.getInstance(project).isOfflineWork = false
    val issueData = GradleIssueData(projectFolderPath.path, UnknownHostException("my host"), null, null)
    val buildIssue = unknownHostIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Unknown host 'my host'. You may need to adjust the proxy settings in Gradle.")
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(ToggleOfflineModeQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenLinkQuickFix::class.java)

  }
}