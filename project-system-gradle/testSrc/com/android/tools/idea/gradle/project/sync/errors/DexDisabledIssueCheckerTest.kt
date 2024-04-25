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
import com.android.tools.idea.gradle.project.sync.quickFixes.AbstractSetJavaLanguageLevelQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelModuleQuickFix
import com.google.common.truth.Truth.assertThat
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Test

class DexDisabledIssueCheckerTest {
  private val INVOKE_CUSTOM = "Invoke-customs are only supported starting with Android O"
  private val DEFAULT_INTERFACE_METHOD = "Default interface methods are only supported starting with Android N (--min-api 24)"
  private val STATIC_INTERFACE_METHOD = "Static interface methods are only supported starting with Android N (--min-api 24)"
  private val FAILED_TASK_MESSAGE = "Execution failed for task ':app:task'."

  private val issueChecker = DexDisabledIssueChecker()

  @Test
  fun `no builder exception causes null issue`() {
    val issueData = GradleIssueData("projectFolderPath", Throwable("Error: $INVOKE_CUSTOM"), null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  @Test
  fun `no RuntimeException causes null issue`() {
    val errorCause = Throwable(DexArchiveBuilderException(FAILED_TASK_MESSAGE, Throwable("Error: $INVOKE_CUSTOM")))
    val issueData = GradleIssueData("projectFolderPath", errorCause, null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  @Test
  fun `no error message causes null issue`() {
    val errorCause = Throwable(DexArchiveBuilderException(FAILED_TASK_MESSAGE, RuntimeException("incorrect message")))
    val issueData = GradleIssueData("projectFolderPath", errorCause, null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  @Test
  fun `unknown message causes null issue`() {
    val errorCause = Throwable(DexArchiveBuilderException(FAILED_TASK_MESSAGE, RuntimeException("Error: unknown error")))
    val issueData = GradleIssueData("projectFolderPath", errorCause, null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  @Test
  fun `invoke-customs message`() {
    verifyWithModule(INVOKE_CUSTOM)
  }

  @Test
  fun `default-interface message`() {
    verifyWithModule(DEFAULT_INTERFACE_METHOD)
  }

  @Test
  fun `static-interface message`() {
    verifyWithModule(STATIC_INTERFACE_METHOD)
  }

  @Test
  fun `no task can be found in message`() {
    val quickFixes = verifyBuildIssueAndGetQuickfixes(INVOKE_CUSTOM, "Task failed")
    assertThat(quickFixes).hasSize(1)
    assertThat(quickFixes[0]).isInstanceOf(SetJavaLanguageLevelAllQuickFix::class.java)
    assertThat((quickFixes[0] as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_1_8)
  }

  @Test
  fun testDexDisabledIssueCheckerIsKnown() {
    assertThat(GradleIssueChecker.getKnownIssuesCheckList().filterIsInstance(DexDisabledIssueChecker::class.java)).isNotEmpty()
  }

  @Test
  fun testCheckIssueHandled() {
    assertThat(
      issueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Error: Invoke-customs are only supported starting with Android O",
        "Caused by: java.lang.RuntimeException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      issueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Error: Default interface methods are only supported starting with Android N (--min-api 24)",
        "Caused by: java.lang.RuntimeException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      issueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Error: Static interface methods are only supported starting with Android N (--min-api 24)",
        "Caused by: java.lang.RuntimeException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    // Check that if the stacktrace is not corresponding to the expected type, the issueChecker doesn't handle the failure.
    assertThat(
      issueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Build failed with Exception: The newly created daemon process has a different context than expected. \n" +
        "what went wrong: \nJava home is different.\n Please check your build files.",
        "Caused by: java.net.SocketException",
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(false)
  }

  private fun verifyWithModule(pattern: String) {
    val quickFixes = verifyBuildIssueAndGetQuickfixes(pattern, FAILED_TASK_MESSAGE)
    assertThat(quickFixes).hasSize(2)
    assertThat(quickFixes.filterIsInstance<SetJavaLanguageLevelAllQuickFix>()).hasSize(1)
    assertThat(quickFixes.filterIsInstance<SetJavaLanguageLevelModuleQuickFix>()).hasSize(1)
    assertThat(quickFixes.map { it.id }).containsNoDuplicates()
    quickFixes.forEach {
      assertThat((it as AbstractSetJavaLanguageLevelQuickFix).level).isEqualTo(LanguageLevel.JDK_1_8)
    }
  }

  private fun verifyBuildIssueAndGetQuickfixes(rootPattern: String, taskPattern: String): List<BuildIssueQuickFix> {
    val errorCause = Throwable(DexArchiveBuilderException(taskPattern, RuntimeException("Error: $rootPattern")))
    val issueData = GradleIssueData("projectFolderPath", errorCause, null, null)
    val buildIssue = issueChecker.check(issueData)
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).startsWith(rootPattern)
    assertThat(buildIssue.description).contains(taskPattern)
    return buildIssue.quickFixes
  }
}

private class DexArchiveBuilderException(message: String, cause: Throwable) : Throwable(message, cause)
