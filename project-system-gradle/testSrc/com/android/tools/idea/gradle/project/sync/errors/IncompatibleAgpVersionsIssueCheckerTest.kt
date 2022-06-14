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

import com.android.tools.idea.gradle.project.sync.AndroidSyncException
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class IncompatibleAgpVersionsIssueCheckerTest : AndroidGradleTestCase() {
  private val incompatibleAgpVersionsIssueChecker = IncompatibleAgpVersionsIssueChecker()

  fun testCheckIssue() {
    val issueData = GradleIssueData(
      projectFolderPath.path,
      AndroidSyncException("Using multiple versions of the Android Gradle Plugin [7.2.1, 7.4] across Gradle builds is not allowed."+
                           "\nAffected builds: [mainProj, included].\n"),
      null,
      null)

    val buildIssue = incompatibleAgpVersionsIssueChecker.check(issueData)
    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue!!.description).contains("Using multiple versions of the Android Gradle Plugin [7.2.1, 7.4] "+
                                                        "across Gradle builds is not allowed.\n" +
                                                        "Affected builds: [mainProj, included].")
    Truth.assertThat(buildIssue.quickFixes).hasSize(0)

  }
}