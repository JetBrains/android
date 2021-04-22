/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.SetSdkDirHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class MissingSdkIssueReporterTest : AndroidGradleTestCase() {
  private lateinit var syncMessages: GradleSyncMessagesStub
  private lateinit var reporter: MissingSdkIssueReporter
  private lateinit var usageReporter: TestSyncIssueUsageReporter

  override fun setUp() {
    super.setUp()

    syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project)
    reporter = MissingSdkIssueReporter()
    usageReporter = TestSyncIssueUsageReporter()
  }

  @Test
  fun testWithSingleModule() {
    syncMessages.removeAllMessages()
    loadSimpleApplication()

    val localPropertiesPath = File(projectFolderPath, SdkConstants.FN_LOCAL_PROPERTIES)
    val syncIssue = setUpMockSyncIssue(localPropertiesPath.absolutePath)

    reporter.report(syncIssue, getModule("app"), null, usageReporter)
    val notifications = syncMessages.notifications
    assertSize(1, notifications)
    val notification = notifications[0]

    assertEquals("Gradle Sync Issues", notification.title)
    assertEquals(
      "SDK location not found. Define a location by setting the ANDROID_SDK_ROOT environment variable or by setting the sdk.dir path in " +
      "your project's local.properties file.\n" +
      "Affected Modules: app", notification.message)
    assertEquals(NotificationCategory.WARNING, notification.notificationCategory)

    val notificationUpdate = syncMessages.notificationUpdate
    val quickFixes = notificationUpdate!!.fixes
    assertSize(1, quickFixes)
    assertInstanceOf(quickFixes[0], SetSdkDirHyperlink::class.java)
    val quickFixPaths = (quickFixes[0] as SetSdkDirHyperlink).localPropertiesPaths
    assertSize(1, quickFixPaths)
    assertContainsElements(quickFixPaths, localPropertiesPath.absolutePath)

    assertEquals(
      listOf(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_SDK_NOT_SET)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SET_SDK_DIR_HYPERLINK)
          .build()),
      usageReporter.collectedIssue)
  }

  @Test
  fun testWithCompositeBuild() {
    syncMessages.removeAllMessages()
    prepareProjectForImport(COMPOSITE_BUILD)
    importProject()

    val localPropertiesPath = File(projectFolderPath, SdkConstants.FN_LOCAL_PROPERTIES)
    val localPropertiesPathTwo = File(projectFolderPath, "TestCompositeLib1/${SdkConstants.FN_LOCAL_PROPERTIES}")
    val localPropertiesPathThree = File(projectFolderPath, "TestCompositeLib3/${SdkConstants.FN_LOCAL_PROPERTIES}")

    val syncIssueOne = setUpMockSyncIssue(localPropertiesPath.absolutePath)
    val syncIssueTwo = setUpMockSyncIssue(localPropertiesPathTwo.absolutePath)
    val syncIssueThree = setUpMockSyncIssue(localPropertiesPathThree.absolutePath)


    val moduleMap = mapOf(
      syncIssueOne to getModule("testWithCompositeBuild"),
      syncIssueTwo to getModule("TestCompositeLib1"),
      syncIssueThree to getModule("TestCompositeLib3")
    )

    reporter.reportAll(listOf(syncIssueOne, syncIssueTwo, syncIssueThree), moduleMap, mapOf(), usageReporter)

    val notifications = syncMessages.notifications.filter { it.notificationCategory == NotificationCategory.WARNING }
    assertSize(1, notifications)
    val notification = notifications[0]

    assertEquals("Gradle Sync Issues", notification.title)
    assertEquals(
      "SDK location not found. Define a location by setting the ANDROID_SDK_ROOT environment variable or by setting the sdk.dir path in " +
      "your project's local.properties files.\n" +
      "Affected Modules: TestCompositeLib1, TestCompositeLib3, testWithCompositeBuild", notification.message)

    val notificationUpdate = syncMessages.notificationUpdate
    val quickFixes = notificationUpdate!!.fixes
    assertSize(1, quickFixes)
    assertInstanceOf(quickFixes[0], SetSdkDirHyperlink::class.java)
    val quickFixPaths = (quickFixes[0] as SetSdkDirHyperlink).localPropertiesPaths
    assertSize(3, quickFixPaths)
    assertContainsElements(quickFixPaths, localPropertiesPath.absolutePath, localPropertiesPathTwo.absolutePath,
                           localPropertiesPathThree.absolutePath)

    assertEquals(
      listOf(GradleSyncIssue
               .newBuilder()
               .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_SDK_NOT_SET)
               .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SET_SDK_DIR_HYPERLINK)
               .build()),
      usageReporter.collectedIssue)
  }

  private fun setUpMockSyncIssue(path: String): IdeSyncIssue {
    val syncIssue = mock(IdeSyncIssue::class.java)
    `when`(syncIssue.data).thenReturn(path)
    `when`(syncIssue.message).thenReturn("This is some message that is not used")
    `when`(syncIssue.severity).thenReturn(IdeSyncIssue.SEVERITY_ERROR)
    `when`(syncIssue.type).thenReturn(IdeSyncIssue.TYPE_SDK_NOT_SET)
    return syncIssue
  }
}
