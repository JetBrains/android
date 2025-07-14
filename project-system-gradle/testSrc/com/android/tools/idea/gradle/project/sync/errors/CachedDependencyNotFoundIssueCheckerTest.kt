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
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Rule
import org.junit.Test

class CachedDependencyNotFoundIssueCheckerTest {
  private val cachedDependencyNotFoundIssueChecker = CachedDependencyNotFoundIssueChecker()

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun testCheckIssue() {
    val expectedNotificationMessage = "No cached version of dependency, available for offline mode."
    val error = "$expectedNotificationMessage\nExtra error message."

    val issueData = GradleIssueData(projectRule.project.basePath!!, Throwable(error), null, null)
    val buildIssue = cachedDependencyNotFoundIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.description).contains(expectedNotificationMessage)
    assertThat((buildIssue.quickFixes.first() as ToggleOfflineModeQuickFix).enableOfflineMode).isFalse()
  }

  @Test
  fun testIssueHandled() {
    assertThat(
      cachedDependencyNotFoundIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "No cached version of dependency, available for offline mode.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isTrue()
  }
}