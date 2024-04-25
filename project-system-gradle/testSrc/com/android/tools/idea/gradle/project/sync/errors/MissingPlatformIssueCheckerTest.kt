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
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat

class MissingPlatformIssueCheckerTest : AndroidGradleTestCase() {
  private val missingPlatformIssueChecker = MissingPlatformIssueChecker()

  fun testGetMissingPlatform() {
    assertThat(getMissingPlatform("Failed to find target with hash string 'android-21' in: /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("failed to find target with hash string 'android-21' in: /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Cause: Failed to find target with hash string 'android-21' in: /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Cause: failed to find target with hash string 'android-21' in: /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Failed to find target android-21 : /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("failed to find target android-21 : /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Cause: Failed to find target android-21 : /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Cause: failed to find target android-21 : /pat/tp/sdk")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Failed to find target android-21")).isEqualTo("android-21")
    assertThat(getMissingPlatform("failed to find target android-21")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Cause: Failed to find target android-21")).isEqualTo("android-21")
    assertThat(getMissingPlatform("Cause: failed to find target android-21")).isEqualTo("android-21")
  }

  fun testCheckIssueHandled() {
    assertThat(
      missingPlatformIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Cause: Failed to find target with hash string 'A.B.C' in: :test",
        "Caused by: java.lang.IllegalStateException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingPlatformIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Cause: Failed to find target 'A.B.C'",
        "Caused by: com.intellij.openapi.externalSystem.model.ExternalSystemException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingPlatformIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Cause: Failed to find target 'A.B.C'",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(false)
  }
}