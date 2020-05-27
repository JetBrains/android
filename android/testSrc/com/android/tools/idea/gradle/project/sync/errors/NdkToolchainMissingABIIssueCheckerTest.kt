/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class NdkToolchainMissingABIIssueCheckerTest: AndroidGradleTestCase() {
  private val ndkToolchainMissingABIIssueChecker = NdkToolchainMissingABIIssueChecker()

  fun testVerifyOld() {
    val errMsg = "No toolchains found in the NDK toolchains folder for ABI with prefix: mipsel-linux-android"
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMsg), null, null)
    val buildIssue = ndkToolchainMissingABIIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("$errMsg\nThis version of the NDK may be incompatible with the Android Gradle " +
                                                  "plugin version 3.0 or older.\n\nPlease use plugin version 3.1 or newer.")
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(FixAndroidGradlePluginVersionQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      ndkToolchainMissingABIIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "No toolchains found in the NDK toolchains folder for ABI with prefix: ",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}