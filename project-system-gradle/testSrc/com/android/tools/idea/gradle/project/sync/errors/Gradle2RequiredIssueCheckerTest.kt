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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.CreateGradleWrapperQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class Gradle2RequiredIssueCheckerTest : AndroidGradleTestCase() {
  private val gradle2RequiredIssueChecker = Gradle2RequiredIssueChecker()

  fun testCheckIssue() {
    val issueData = GradleIssueData(projectFolderPath.path,
                                    Throwable("Wrong gradle version.\norg/codehaus/groovy/runtime/typehandling/ShortTypeHandling"),
                                    null,
                                    null)

    val buildIssue = gradle2RequiredIssueChecker.check(issueData)
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Gradle " + SdkConstants.GRADLE_MINIMUM_VERSION + " is required.")
    // Verify quickFix.
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(CreateGradleWrapperQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      gradle2RequiredIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Build failed with Exception: org/codehaus/groovy/runtime/typehandling/ShortTypeHandling",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}