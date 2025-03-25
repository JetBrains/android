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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Test

class UnknownMultiplatformTestSourceSetCheckerTest {
  private val issueChecker = UnknownMultiplatformTestSourceSetChecker()

  @Test
  fun testCheckIssue() {
    val expectedNotificationMessage = "KotlinSourceSet with name 'androidTestOnDevice' not found"
    val error = "$expectedNotificationMessage:\nExtra error message."

    val issueData = GradleIssueData("", Throwable(error), null, null)
    val buildIssue = issueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue?.description).contains("The default source sets for Android KMP target were renamed in AGP 8.9.0-alpha03.")
  }

  @Test
  fun testIssueHandled() {
    assertThat(
      issueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "KotlinSourceSet with name 'androidTestOnJvm' not found",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}