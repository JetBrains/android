/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.testing.TestMessagesDialog
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

class GradleJvmIncompatibleDialogTest : LightPlatformTestCase() {

  private lateinit var gradleJvmCompatibility: GradleJvmCompatibilityInfo
  private lateinit var applyCompatibleJvmAction: DescribedBuildIssueQuickFix
  private lateinit var openSettingsAction: DescribedBuildIssueQuickFix
  private lateinit var currentJavaVersion: JavaVersion

  override fun setUp() {
    super.setUp()
    gradleJvmCompatibility =
      GradleJvmCompatibilityInfo(
        GradleVersion.version("8.0"),
        JavaVersion.compose(17),
        listOf(JavaVersion.compose(16), JavaVersion.compose(17)),
      )
    currentJavaVersion = JavaVersion.compose(11)
    applyCompatibleJvmAction = mock(DescribedBuildIssueQuickFix::class.java)
    openSettingsAction = mock(DescribedBuildIssueQuickFix::class.java)
  }

  override fun tearDown() {
    super.tearDown()
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  fun `test show dialog displays correct message`() {
    val testDialog = TestMessagesDialog(0)
    TestDialogManager.setTestDialog(testDialog)

    showGradleJvmIncompatibleDialog()

    val expectedMessage =
      "The project's Gradle version Gradle 8.0 is incompatible with the Gradle JVM version 11. To fix this, select a JVM " +
        "version that is at least 16 and at most 17."
    assertEquals(expectedMessage, testDialog.displayedMessage)
  }

  fun `test show dialog select apply compatible jvm runs action`() {
    TestDialogManager.setTestDialog(TestMessagesDialog(0))

    showGradleJvmIncompatibleDialog()
    verify(applyCompatibleJvmAction).runQuickFix(project, DataContext.EMPTY_CONTEXT)
    verify(openSettingsAction, never()).runQuickFix(any(), any())
  }

  fun `test show dialog select open settings runs action`() {
    TestDialogManager.setTestDialog(TestMessagesDialog(1))

    showGradleJvmIncompatibleDialog()
    verify(openSettingsAction).runQuickFix(project, DataContext.EMPTY_CONTEXT)
    verify(applyCompatibleJvmAction, never()).runQuickFix(any(), any())
  }

  fun `test show dialog cancel runs no action`() {
    TestDialogManager.setTestDialog(TestMessagesDialog(-1))

    showGradleJvmIncompatibleDialog()
    verify(openSettingsAction, never()).runQuickFix(any(), any())
    verify(applyCompatibleJvmAction, never()).runQuickFix(any(), any())
  }

  private fun showGradleJvmIncompatibleDialog() {
    GradleJvmIncompatibleDialog.showDialog(
      project,
      currentJavaVersion,
      gradleJvmCompatibility,
      applyCompatibleJvmAction,
      openSettingsAction,
    )
  }
}
