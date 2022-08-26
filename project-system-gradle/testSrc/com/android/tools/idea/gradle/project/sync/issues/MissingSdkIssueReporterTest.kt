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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.SdkConstants
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.tools.idea.gradle.project.sync.hyperlink.SetSdkDirHyperlink
import com.android.tools.idea.project.messages.MessageType
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import org.junit.Test
import java.io.File
import java.util.IdentityHashMap

class MissingSdkIssueReporterTest : AndroidGradleTestCase() {
  private lateinit var reporter: MissingSdkIssueReporter
  private lateinit var usageReporter: TestSyncIssueUsageReporter

  override fun setUp() {
    super.setUp()

    reporter = MissingSdkIssueReporter()
    usageReporter = TestSyncIssueUsageReporter()
  }

  @Test
  fun testWithSingleModule() {
    loadSimpleApplication()

    val localPropertiesPath = File(projectFolderPath, SdkConstants.FN_LOCAL_PROPERTIES)
    val syncIssue = setUpMockSyncIssue(localPropertiesPath.absolutePath)

    val messages = reporter.report(syncIssue, getModule("app"), null)
    assertSize(1, messages)
    val notification = messages[0]

    assertEquals("Gradle Sync Issues", notification.group)
    assertEquals(
      "SDK location not found. Define a location by setting the ANDROID_SDK_ROOT environment variable or by setting the sdk.dir path in " +
        "your project's local.properties file.\n" +
        "<a href=\"set.sdkdir\">Set sdk.dir in local.properties and sync project</a>\n" +
        "Affected Modules: app",
      notification.message
    )
    assertEquals(MessageType.WARNING, notification.type)

    val quickFixes = messages[0].quickFixes
    assertSize(1 + 1 /* affected modules */, quickFixes)
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
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_SDK_NOT_SET, messages))
  }

  @Test
  fun testWithCompositeBuild() {
    prepareProjectForImport(COMPOSITE_BUILD)
    importProject()

    val localPropertiesPath = File(projectFolderPath, SdkConstants.FN_LOCAL_PROPERTIES)
    val localPropertiesPathTwo = File(projectFolderPath, "TestCompositeLib1/${SdkConstants.FN_LOCAL_PROPERTIES}")
    val localPropertiesPathThree = File(projectFolderPath, "TestCompositeLib3/${SdkConstants.FN_LOCAL_PROPERTIES}")

    val syncIssueOne = setUpMockSyncIssue(localPropertiesPath.absolutePath)
    val syncIssueTwo = setUpMockSyncIssue(localPropertiesPathTwo.absolutePath)
    val syncIssueThree = setUpMockSyncIssue(localPropertiesPathThree.absolutePath)


    val moduleMap = listOf(
      syncIssueOne to getModule("testWithCompositeBuild"),
      syncIssueTwo to getModule("TestCompositeLib1"),
      syncIssueThree to getModule("TestCompositeLib3")
    ).toMap(IdentityHashMap())

    val messages = reporter
      .reportAll(
        listOf(syncIssueOne, syncIssueTwo, syncIssueThree),
        moduleMap,
        mapOf()
      )
      .filter { it.type == MessageType.WARNING }

    assertSize(1, messages)
    val notification = messages[0]

    assertEquals("Gradle Sync Issues", notification.group)
    assertEquals(
      "SDK location not found. Define a location by setting the ANDROID_SDK_ROOT environment variable or by setting the sdk.dir path in " +
        "your project's local.properties files.\n" +
        "<a href=\"set.sdkdir\">Set sdk.dir in local.properties and sync project</a>\n" +
        "Affected Modules: TestCompositeLib1, TestCompositeLib3, testWithCompositeBuild",
      notification.message
    )

    val quickFixes = messages[0]!!.quickFixes
    assertSize(1 + 1 /* affected modules */, quickFixes)
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
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_SDK_NOT_SET, messages))
  }

  private fun setUpMockSyncIssue(path: String): IdeSyncIssue {
    return IdeSyncIssueImpl(
      data = path,
      severity = IdeSyncIssue.SEVERITY_ERROR,
      message = "This is some message that is not used",
      type = IdeSyncIssue.TYPE_SDK_NOT_SET,
      multiLineMessage = null
    )
  }
}
