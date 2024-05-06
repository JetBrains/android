/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.truth.Truth
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Test
import java.io.IOException

class AarDependencyCompatibilityIssueCheckerTest {
  private val MODULE_COMPILED_AGAINST_PATTERN = """<module> is currently compiled against <currentCompileSdk>."""
  private val COMPILE_SDK_UPDATE_RECOMMENDED_ACTION_PATTERN = """Recommended action: Update this project to use a newer compileSdk of at least <minCompileSdk>, for example <minCompileSdk>."""
  private val DEPENDENCY_PATTERN = """Dependency '<dependency>' requires libraries and applications that depend on it to compile against version <minCompileSdk> or later of the Android APIs."""
  private val COMPILE_SDK_ISSUE_PATTERN = """$DEPENDENCY_PATTERN $MODULE_COMPILED_AGAINST_PATTERN $COMPILE_SDK_UPDATE_RECOMMENDED_ACTION_PATTERN"""
  private val UPDATE_LINK = "<a href=\"update.modules.minCompileSdk\">Update minCompileSdk in modules with dependencies that require a higher minCompileSdk.</a>"

  private val issueChecker = AarDependencyCompatibilityIssueChecker()

  @Test
  fun `no builder exception causes null issue`() {
    val issueData = GradleIssueData("projectFolderPath", Throwable(COMPILE_SDK_ISSUE_PATTERN), null, null)
    val buildIssue = issueChecker.check(issueData)
    Truth.assertThat(buildIssue).isNull()
  }

  @Test
  fun `no RuntimeException causes null issue`() {
    val issueData = GradleIssueData("projectFolderPath", Throwable(IOException(COMPILE_SDK_ISSUE_PATTERN)), null, null)
    val buildIssue = issueChecker.check(issueData)
    Truth.assertThat(buildIssue).isNull()
  }

  @Test
  fun `no error message causes null issue`() {
    val issueData = GradleIssueData("projectFolderPath", Throwable(RuntimeException("unexpected message")), null, null)
    val buildIssue = issueChecker.check(issueData)
    Truth.assertThat(buildIssue).isNull()
  }

  @Test
  fun `produce a build issue with a quick fix`() {
    val rootMessage = getRootMessage(":app", "29", "34", "abc:cde:fgh")
    val issueData = GradleIssueData("projectFolderPath", Throwable(RuntimeException(rootMessage)), null, null)
    val buildIssue = issueChecker.check(issueData)
    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue?.description).isEqualTo("$rootMessage\n$UPDATE_LINK")
    Truth.assertThat(buildIssue?.quickFixes).hasSize(1)
    val quickFix = buildIssue?.quickFixes?.get(0)
    Truth.assertThat(quickFix).isInstanceOf(UpdateCompileSdkQuickFix::class.java)
  }

  @Test
  fun `produce a build issue with a quick fix to update to highest minCompileSdk`() {
    val rootMessage1 = getRootMessage(":app", "29", "33", "abc:cde:fgh")
    val rootMessage2 = getRootMessage(":app", "29", "34", "ijk:lmn:opq")
    val rootMessage = "$rootMessage1\n\n$rootMessage2"
    val issueData = GradleIssueData("projectFolderPath", Throwable(RuntimeException(rootMessage)), null, null)
    val buildIssue = issueChecker.check(issueData)
    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue?.description).isEqualTo("$rootMessage\n$UPDATE_LINK")
    Truth.assertThat(buildIssue?.quickFixes).hasSize(1)
    val quickFix = buildIssue?.quickFixes?.get(0)
    Truth.assertThat(quickFix).isInstanceOf(UpdateCompileSdkQuickFix::class.java)
    val modulesWithMinCompileSdk = (quickFix as UpdateCompileSdkQuickFix).modulesWithSuggestedMinCompileSdk
    Truth.assertThat(modulesWithMinCompileSdk).hasSize(1)
    Truth.assertThat(modulesWithMinCompileSdk[":app"]).isEqualTo(34)
  }

  @Test
  fun `produce build issues with a quick fixes to update to minCompileSdk for each module`() {
    val rootMessage1 = getRootMessage(":app", "29", "34", "abc:cde:fgh")
    val rootMessage2 = getRootMessage(":app1", "28", "33", "ijk:lmn:opq")
    val rootMessage = "$rootMessage1\n\n$rootMessage2"
    val issueData = GradleIssueData("projectFolderPath", Throwable(RuntimeException(rootMessage)), null, null)
    val buildIssue = issueChecker.check(issueData)
    Truth.assertThat(buildIssue).isNotNull()
    Truth.assertThat(buildIssue?.description).isEqualTo("$rootMessage\n$UPDATE_LINK")
    Truth.assertThat(buildIssue?.quickFixes).hasSize(1)
    val quickFix = buildIssue?.quickFixes?.get(0)
    Truth.assertThat(quickFix).isInstanceOf(UpdateCompileSdkQuickFix::class.java)
    val modulesWithMinCompileSdk = (quickFix as UpdateCompileSdkQuickFix).modulesWithSuggestedMinCompileSdk
    Truth.assertThat(modulesWithMinCompileSdk).hasSize(2)
    Truth.assertThat(modulesWithMinCompileSdk[":app"]).isEqualTo(34)
    Truth.assertThat(modulesWithMinCompileSdk[":app1"]).isEqualTo(33)
  }


  private fun getRootMessage(moduleName: String, currentCompileSdk: String, minCompileSdk: String, dependency: String) =
   COMPILE_SDK_ISSUE_PATTERN
    .replace("<module>", moduleName)
    .replace("<currentCompileSdk>", currentCompileSdk)
    .replace("<minCompileSdk>", minCompileSdk)
    .replace("<dependency>", dependency)
}