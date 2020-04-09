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

import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class UnsupportedModelVersionIssueCheckerTest: AndroidGradleTestCase() {
  private val unsupportedModelVersionIssueChecker = UnsupportedModelVersionIssueChecker()

  fun testCheckIssue() {
    val message = "The project is using an unsupported version of the Android Gradle plug-in"
    val issueData = GradleIssueData(projectFolderPath.path,
                                    Throwable("The project is using an unsupported version of the Android Gradle plug-in"),
                                    null,
                                    null)
    val buildIssue = unsupportedModelVersionIssueChecker.check(issueData)

    assertThat(buildIssue!!.description).contains(message)
    assertThat(buildIssue!!.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(FixAndroidGradlePluginVersionQuickFix::class.java)
  }
}