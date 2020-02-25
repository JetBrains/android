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

import com.android.tools.idea.gradle.project.sync.errors.ClassLoadingIssueChecker.StopGradleDaemonQuickFix
import com.android.tools.idea.gradle.project.sync.errors.ClassLoadingIssueChecker.SyncProjectQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import junit.framework.TestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class ClassLoadingIssueCheckerTest : AndroidGradleTestCase() {
  private val classLoadingIssueChecker = ClassLoadingIssueChecker()

  fun testCheckIssueWhenMethodNotFound() {
    assertErrorAndHyperlinksDisplayed(NoSuchMethodError("org.slf4j.spi.LocationAwareLogger.log"), GradleSyncFailure.METHOD_NOT_FOUND)
  }

  fun testCheckIssueWhenClassCannotBeCast() {
    assertErrorAndHyperlinksDisplayed(
      Throwable("Cause: org.slf4j.impl.JDK14LoggerFactory cannot be cast to ch.qos.logback.classic.LoggerContext"),
      GradleSyncFailure.CANNOT_BE_CAST_TO)
  }

  private fun assertErrorAndHyperlinksDisplayed(@NotNull cause: Throwable, @Nullable syncFailure: AndroidStudioEvent.GradleSyncFailure) {
    val issueData = GradleIssueData(projectFolderPath.path, cause, null, null)
    val buildIssue = classLoadingIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    val message = buildIssue!!.description
    assertThat(message).contains("Gradle's dependency cache may be corrupt")
    assertThat(message).contains("Re-download dependencies and sync project")
    assertThat(message)
      .contains("In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.")

    val restartCapable = ApplicationManager.getApplication().isRestartCapable
    val quickFixText = if (restartCapable) "Stop Gradle build processes (requires restart)" else "Open Gradle Daemon documentation"
    TestCase.assertTrue(message.contains(quickFixText))

    // Verify QuickFixes.
    val quickFixes = buildIssue.quickFixes
    assertThat(quickFixes).hasSize(2)
    assertThat(quickFixes[0]).isInstanceOf(SyncProjectQuickFix::class.java)
    assertThat(quickFixes[1]).isInstanceOf(StopGradleDaemonQuickFix::class.java)
  }
}