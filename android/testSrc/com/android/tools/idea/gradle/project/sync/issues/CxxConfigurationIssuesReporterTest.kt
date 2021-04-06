/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallNdkHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class CxxConfigurationIssuesReporterTest : AndroidGradleTestCase() {
  private lateinit var syncMessages: GradleSyncMessagesStub
  private lateinit var reporter: CxxConfigurationIssuesReporter
  private lateinit var usageReporter: TestSyncIssueUsageReporter

  override fun setUp() {
    super.setUp()

    syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project)
    reporter = CxxConfigurationIssuesReporter()
    usageReporter = TestSyncIssueUsageReporter()
  }

  @Test
  fun testWithSingleModule() {
    syncMessages.removeAllMessages()
    loadSimpleApplication()

    val syncIssue = setUpMockSyncIssue("19.1.2")

    reporter.report(syncIssue, getModule("app"), null, usageReporter)
    val notifications = syncMessages.notifications
    assertSize(1, notifications)
    val notification = notifications[0]

    assertEquals("Gradle Sync Issues", notification.title)
    assertEquals(
      "No version of NDK matched the requested version 19.1.2\n" +
      "Affected Modules: app", notification.message)
    assertEquals(NotificationCategory.WARNING, notification.notificationCategory)

    val notificationUpdate = syncMessages.notificationUpdate
    val quickFixes = notificationUpdate!!.fixes
    assertSize(1, quickFixes)
    assertInstanceOf(quickFixes[0], InstallNdkHyperlink::class.java)

    assertEquals(
      listOf(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.INSTALL_NDK_HYPERLINK)
          .build()),
      usageReporter.collectedIssue)
  }

  @Test
  fun testWithCompositeBuild() {
    syncMessages.removeAllMessages()
    prepareProjectForImport(COMPOSITE_BUILD)
    importProject()

    val syncIssueOne = setUpMockSyncIssue("19.1.2")
    val syncIssueTwo = setUpMockSyncIssue("19.1.1")
    val syncIssueThree = setUpMockSyncIssue("19.1.2") // Intentional duplicate of syncIssueOne


    val moduleMap = mapOf(
      syncIssueOne to getModule("testWithCompositeBuild"),
      syncIssueTwo to getModule("TestCompositeLib1"),
      syncIssueThree to getModule("TestCompositeLib3")
    )

    reporter.reportAll(listOf(syncIssueOne, syncIssueTwo, syncIssueThree), moduleMap, mapOf(), usageReporter)

    val notifications = syncMessages.notifications.filter { it.notificationCategory == NotificationCategory.WARNING }
    assertSize(1, notifications)
    val notificationOne = notifications[0]

    assertEquals("Gradle Sync Issues", notificationOne.title)
    assertEquals(
      "No version of NDK matched the requested version 19.1.2\n" +
      "Affected Modules: TestCompositeLib1, TestCompositeLib3, testWithCompositeBuild", notificationOne.message)

    val notificationUpdate = syncMessages.notificationUpdate
    val quickFixes = notificationUpdate!!.fixes
    assertSize(1, quickFixes)
    assertInstanceOf(quickFixes[0], InstallNdkHyperlink::class.java)

    val resultSyncIssue = GradleSyncIssue
      .newBuilder()
      .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION)
      .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.INSTALL_NDK_HYPERLINK)
      .build()
    assertEquals(
      listOf(resultSyncIssue),
      usageReporter.collectedIssue)
  }

  private fun setUpMockSyncIssue(revision: String): IdeSyncIssue {
    val syncIssue = mock(IdeSyncIssue::class.java)
    `when`(syncIssue.data).thenReturn(null)
    `when`(syncIssue.message).thenReturn("No version of NDK matched the requested version $revision")
    `when`(syncIssue.severity).thenReturn(IdeSyncIssue.SEVERITY_ERROR)
    `when`(syncIssue.type).thenReturn(IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION)
    return syncIssue
  }
}