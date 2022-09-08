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
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.project.messages.MessageType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.testFramework.UsefulTestCase.assertSize
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.IdentityHashMap

@RunsInEdt
class MissingSdkIssueReporterTest {
  @get:Rule
  var projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  private val reporter = MissingSdkIssueReporter()

  @Test
  fun testWithSingleModule() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->

      val localPropertiesPath = preparedProject.root.resolve(SdkConstants.FN_LOCAL_PROPERTIES)
      val syncIssue = setUpMockSyncIssue(localPropertiesPath.absolutePath)

      val messages = reporter.report(syncIssue, project.gradleModule(":app")!!, null)
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
            .build()
        ),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_SDK_NOT_SET, messages)
      )
    }
  }

  @Test
  fun testWithCompositeBuild() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.COMPOSITE_BUILD)
    preparedProject.open { project ->

      val localPropertiesPath = preparedProject.root.resolve(SdkConstants.FN_LOCAL_PROPERTIES)
      val localPropertiesPathTwo = preparedProject.root.resolve("TestCompositeLib1/${SdkConstants.FN_LOCAL_PROPERTIES}")
      val localPropertiesPathThree = preparedProject.root.resolve("TestCompositeLib3/${SdkConstants.FN_LOCAL_PROPERTIES}")

      val syncIssueOne = setUpMockSyncIssue(localPropertiesPath.absolutePath)
      val syncIssueTwo = setUpMockSyncIssue(localPropertiesPathTwo.absolutePath)
      val syncIssueThree = setUpMockSyncIssue(localPropertiesPathThree.absolutePath)


      val moduleMap = listOf(
        syncIssueOne to project.gradleModule(":")!!,
        syncIssueTwo to project.gradleModule(":TestCompositeLib1")!!,
        syncIssueThree to project.gradleModule(":TestCompositeLib3")!!
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
          "Affected Modules: TestCompositeLib1, TestCompositeLib3, project",
        notification.message
      )

      val quickFixes = messages[0]!!.quickFixes
      assertSize(1 + 1 /* affected modules */, quickFixes)
      assertInstanceOf(quickFixes[0], SetSdkDirHyperlink::class.java)
      val quickFixPaths = (quickFixes[0] as SetSdkDirHyperlink).localPropertiesPaths
      assertSize(3, quickFixPaths)
      assertContainsElements(
        quickFixPaths, localPropertiesPath.absolutePath, localPropertiesPathTwo.absolutePath,
        localPropertiesPathThree.absolutePath
      )

      assertEquals(
        listOf(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_SDK_NOT_SET)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SET_SDK_DIR_HYPERLINK)
            .build()
        ),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_SDK_NOT_SET, messages)
      )
    }
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
