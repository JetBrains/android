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

import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.replaceService
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Ignore
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/**
 * Tests for [MissingNdkIssueChecker].
 */
class MissingNdkIssueCheckerTest : AndroidGradleTestCase() {
  private val missingNdkIssueChecker = MissingNdkIssueChecker()
  fun testNullErrorMessage() {
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(null, null), null, null)
    val buildIssue = MissingNdkIssueChecker().check(issueData)
    assertThat(buildIssue).isNull()
  }

  fun testPatterns() {
    assertThat(
      tryExtractPreferredNdkDownloadVersion(
        "No version of NDK matched the requested version 19.2.5345600. Versions available locally: 20.0.5471264-rc3")!!.toString())
      .isEqualTo("19.2.5345600")
    assertThat(
      tryExtractPreferredNdkDownloadVersion(
        "No version of NDK matched the requested version 19.2.5345600")!!.toString())
      .isEqualTo("19.2.5345600")
    assertThat(
      tryExtractPreferredNdkDownloadVersion(
        "NDK not configured. Download it with SDK manager. Preferred NDK version is '19.2.5345600'")!!.toString())
      .isEqualTo("19.2.5345600")
    assertThat(
      tryExtractPreferredNdkDownloadVersion(
        "No version of NDK matched the requested version 19.2")!!.toString())
      .isEqualTo("19.2")
  }

  fun testHandleErrorWithNdkLicenceMissing() {
    verifyWithFixVersion("java.Lang.RuntimeException: Failed to install the following Android SDK packages as some licences have not been accepted." +
                           "blah blah ndk-bundle NDK blah blah")
  }

  fun testHandleErrorWithNdkInstallFailed() {
    verifyWithFixVersion("Failed to install the following SDK components: blah blah ndk-bundle NDK blah blah")
  }

  fun testPreferredVersionNotAlreadyInstalled() {
    verifyWithInstall("No version of NDK matched the requested version 19.1.preferred")
  }

  fun testPreferredVersionAlreadyInstalled() {
    val localPackage = mock(LocalPackage::class.java, RuntimeExceptionAnswer())
    doReturn(Revision.parseRevision("19.1.3")).whenever(localPackage).version
    IdeSdks.getInstance().setSpecificLocalPackage("ndk;19.1.3", localPackage)
    verifyWithFixVersion("No version of NDK matched the requested version 19.1.3")
  }

  fun testHandleErrorWithNdkNotConfigured() {
    verifyWithFixVersion("NDK not configured. /some/path")
  }

  fun testHandleErrorWithNdkLocationNotFound() {
    verifyWithFixVersion("NDK location not found. Define location with ndk.dir in the local.properties file " +
                         "or with an ANDROID_NDK_HOME environment variable.")
  }

  @Ignore("b/356225801")
  fun testNotInstalledNoPreferred() {
    val spyIdeSdks = spy(IdeSdks.getInstance())
    doReturn(null).whenever(spyIdeSdks).getHighestLocalNdkPackage(anyBoolean())
    project.replaceService(IdeSdks::class.java, spyIdeSdks, project)
    verifyWithInstall("NDK location not found.")
  }

  fun testOldAndroidGradlePluginDoesNotReturnAnything() {
    val buildEnvironment = mock(BuildEnvironment::class.java)
    val gradle = mock(GradleEnvironment::class.java)
    doReturn(gradle).whenever(buildEnvironment).gradle
    doReturn("6.2").whenever(gradle).gradleVersion

    val issueData = GradleIssueData(projectFolderPath.path, Throwable("NDK not configured."), buildEnvironment, null)
    val buildIssue = missingNdkIssueChecker.check(issueData)
    assertThat(buildIssue).isNull()
  }

  fun testLessOldGradlePluginDoesReturnSomething() {
    val buildEnvironment = mock(BuildEnvironment::class.java)
    val gradle = mock(GradleEnvironment::class.java)
    doReturn(gradle).whenever(buildEnvironment).gradle
    doReturn("6.3").whenever(gradle).gradleVersion
    val issueData = GradleIssueData(projectFolderPath.path, Throwable("NDK not configured."), null, null)
    val buildIssue = missingNdkIssueChecker.check(issueData)
    assertThat(buildIssue).isNotNull()
  }

  fun testCheckIssueHandled() {
    assertThat(
      missingNdkIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "NDK not configured. Download it with SDK manager. Preferred NDK version is '45.2.0'",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingNdkIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "No version of NDK matched the requested version 45.26.0",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingNdkIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "NDK location not found.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingNdkIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Specified android.ndkVersion A.B.C does not have enough precision.\n Please fix.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      missingNdkIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Failed to install the following Android SDK packages as some licences have not been accepted. NDK location not found.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }

  private fun verifyWithFixVersion(errMsg: String) {
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMsg), null, null)
    val buildIssue = missingNdkIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.title).isEqualTo("NDK not configured.")
    assertThat(buildIssue.description).startsWith(errMsg)
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(MissingNdkIssueChecker.FixNdkVersionQuickFix::class.java)
  }

  private fun verifyWithInstall(errMsg: String) {
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMsg), null, null)
    val buildIssue = missingNdkIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.title).isEqualTo("NDK not configured.")
    assertThat(buildIssue.description).startsWith(errMsg)
    assertThat(buildIssue.quickFixes).hasSize(1)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(MissingNdkIssueChecker.InstallNdkQuickFix::class.java)
  }

  class RuntimeExceptionAnswer : Answer<Any> {
    override fun answer(invocation: InvocationOnMock): Any {
      throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
    }
  }
}