/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
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
    verifyWithFixVersion("Failed to install the following Android SDK packages as some licences have not been accepted." +
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
    doReturn(Revision.parseRevision("19.1.3")).`when`(localPackage).version
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

  fun testNotInstalledNoPreferred() {
    val spyIdeSdks = spy(IdeSdks.getInstance())
    IdeComponents(project).replaceApplicationService(IdeSdks::class.java, spyIdeSdks)
    doReturn(null).`when`(spyIdeSdks).getHighestLocalNdkPackage(anyBoolean())
    verifyWithInstall("NDK location not found.")
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