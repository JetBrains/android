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
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File

class FailedToParseSdkIssueCheckerTest: AndroidGradleTestCase() {
  private val failedToParseSdkIssueChecker = FailedToParseSdkIssueChecker()

  fun testCheckIssueWithoutBrokenSdk() {
    val issueData = GradleIssueData(projectFolderPath.path, RuntimeException("failed to parse SDK"), null, null)
    val buildIssue = failedToParseSdkIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("failed to parse SDK")
    assertThat(buildIssue.quickFixes).hasSize(0)
  }

  fun testCheckIssueWithBrokenSdkAndroidNoWriteAccess() {
    val sdkPath: File = object : File("/path/to/sdk/home") {
      override fun canWrite(): Boolean {
        return false
      }
    }
    val mockChecker = Mockito.spy(failedToParseSdkIssueChecker)
    whenever(mockChecker.findPathOfSdkWithoutAddonsFolder(projectFolderPath.path)).thenReturn(sdkPath)

    val issueData = GradleIssueData(projectFolderPath.path, RuntimeException("failed to parse SDK"), null, null)
    val buildIssue = mockChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(
      ("The directory 'add-ons', in the Android SDK at '${sdkPath.absolutePath}', is either missing or empty\n\n" +
       "Current user ('${SystemProperties.getUserName()}') does not have write access to the SDK directory."))
    assertThat(buildIssue.quickFixes).hasSize(0)
  }

  fun testCheckIssueWithBrokenSdkAndroidWriteAccess() {
    val sdkPath: File = object : File("/path/to/sdk/home") {
      override fun canWrite(): Boolean {
        return true
      }
    }
    val mockChecker = Mockito.spy(failedToParseSdkIssueChecker)
    whenever(mockChecker.findPathOfSdkWithoutAddonsFolder(projectFolderPath.path)).thenReturn(sdkPath)

    val issueData = GradleIssueData(projectFolderPath.path, RuntimeException("failed to parse SDK"), null, null)
    val buildIssue = mockChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(
      "The directory 'add-ons', in the Android SDK at '${sdkPath.absolutePath}', is either missing or empty")
    assertThat(buildIssue.quickFixes).hasSize(0)
  }

  fun testCheckIssueHandled() {
    assertThat(
      failedToParseSdkIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Build failed with Exception: failed to parse SDK",
        "Caused by: java.lang.RuntimeException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      failedToParseSdkIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Build failed with Exception: failed to parse SDK",
        "Caused by: java.net.SocketException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(false)
  }
}