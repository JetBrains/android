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
import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerNullMessageSyncErrorToSimulate
import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.FAILED_TO_INSTALL_NDK_BUNDLE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.NDK_NOT_CONFIGURED

import com.android.tools.idea.gradle.project.sync.hyperlink.FixNdkVersionHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallNdkHyperlink
import com.android.tools.idea.gradle.project.sync.issues.TestSyncIssueUsageReporter
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.*
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.*
import junit.framework.TestCase
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/**
 * Tests for [MissingNdkErrorHandler].
 */
class MissingNdkErrorHandlerTest : AndroidGradleTestCase() {
  private lateinit var mySyncMessagesStub: GradleSyncMessagesStub
  private lateinit var myUsageReporter: TestSyncIssueUsageReporter

  override fun setUp() {
    super.setUp()
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(project, testRootDisposable)
    myUsageReporter = TestSyncIssueUsageReporter.replaceSyncMessagesService(project, testRootDisposable)
  }

  fun testNullErrorMessage() {
    registerNullMessageSyncErrorToSimulate()
    loadProjectAndExpectNullNotification()
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
    val errMsg = "Failed to install the following Android SDK packages as some licences have not been accepted. blah blah ndk-bundle NDK blah blah"
    registerSyncErrorToSimulate(errMsg)
    loadProjectAndExpectFixNdkVersionHyperlink(
      "Failed to install the following Android SDK packages as some licences have not been accepted. blah blah ndk-bundle NDK blah blah",
      listOf(), FAILED_TO_INSTALL_NDK_BUNDLE)
  }

  fun testHandleErrorWithNdkInstallFailed() {
    val errMsg = "Failed to install the following SDK components: blah blah ndk-bundle NDK blah blah"
    registerSyncErrorToSimulate(errMsg)
    loadProjectAndExpectFixNdkVersionHyperlink("Failed to install the following SDK components: blah blah ndk-bundle NDK blah blah",
                                               listOf(), FAILED_TO_INSTALL_NDK_BUNDLE)
  }

  fun testPreferredVersionNotAlreadyInstalled() {
    val errMsg = "No version of NDK matched the requested version 19.1.preferred"
    registerSyncErrorToSimulate(errMsg)
    loadProjectAndExpectInstallNdkVersionHyperlink(
      errMsg,
      listOf(INSTALL_NDK_HYPERLINK),
      NDK_NOT_CONFIGURED)
  }

  fun testPreferredVersionAlreadyInstalled() {
    val errMsg = "No version of NDK matched the requested version 19.1.3"
    val localPackage = mock(LocalPackage::class.java, RuntimeExceptionAnswer())
    doReturn(Revision.parseRevision("19.1.3")).`when`(localPackage).version
    IdeSdks.getInstance().setSpecificLocalPackage("ndk;19.1.3", localPackage)

    registerSyncErrorToSimulate(errMsg)
    loadProjectAndExpectFixNdkVersionHyperlink(
      errMsg,
      listOf(),
      NDK_NOT_CONFIGURED)
  }

  fun testHandleErrorWithNdkNotConfigured() {
    registerSyncErrorToSimulate("NDK not configured. /some/path")
    loadProjectAndExpectFixNdkVersionHyperlink("NDK not configured.", listOf(), NDK_NOT_CONFIGURED)
  }

  fun testHandleErrorWithNdkLocationNotFound() {
    registerSyncErrorToSimulate(
      "NDK location not found. Define location with ndk.dir in the local.properties file " + "or with an ANDROID_NDK_HOME environment variable.")
    loadProjectAndExpectFixNdkVersionHyperlink("NDK not configured.", listOf(), NDK_NOT_CONFIGURED)
  }

  private fun loadProjectAndExpectNullNotification() {
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION)

    val notificationUpdate = mySyncMessagesStub.notificationUpdate
    assertThat(notificationUpdate).isNull()
  }

  private fun loadProjectAndExpectFixNdkVersionHyperlink(expected: String,
                                                         syncQuickFixes: Collection<GradleSyncQuickFix>,
                                                         syncFailure: GradleSyncFailure) {
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION)

    val notificationUpdate = mySyncMessagesStub.notificationUpdate
    TestCase.assertNotNull(notificationUpdate)

    assertThat(notificationUpdate!!.text).isEqualTo(expected)

    // Verify hyperlinks are correct.
    val quickFixes = notificationUpdate.fixes
    assertThat(quickFixes).hasSize(1)
    assertThat(quickFixes[0]).isInstanceOf(FixNdkVersionHyperlink::class.java)

    TestCase.assertEquals(syncFailure, myUsageReporter.collectedFailure)
    TestCase.assertEquals(syncQuickFixes, myUsageReporter.collectedQuickFixes)
  }

  private fun loadProjectAndExpectInstallNdkVersionHyperlink(expected: String,
                                                             syncQuickFixes: Collection<GradleSyncQuickFix>,
                                                             syncFailure: GradleSyncFailure) {
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION)

    val notificationUpdate = mySyncMessagesStub.notificationUpdate
    TestCase.assertNotNull(notificationUpdate)

    assertThat(notificationUpdate!!.text).isEqualTo(expected)

    // Verify hyperlinks are correct.
    val quickFixes = notificationUpdate.fixes
    assertThat(quickFixes).hasSize(1)
    assertThat(quickFixes[0]).isInstanceOf(InstallNdkHyperlink::class.java)

    TestCase.assertEquals(syncFailure, myUsageReporter.collectedFailure)
    TestCase.assertEquals(syncQuickFixes, myUsageReporter.collectedQuickFixes)
  }

  class RuntimeExceptionAnswer : Answer<Any> {
    override fun answer(invocation: InvocationOnMock): Any {
      throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
    }
  }
}