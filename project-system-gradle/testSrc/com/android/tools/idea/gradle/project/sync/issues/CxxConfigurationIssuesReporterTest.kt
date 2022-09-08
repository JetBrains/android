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

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallNdkHyperlink
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
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.testFramework.UsefulTestCase.assertSize
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.IdentityHashMap

@RunsInEdt
class CxxConfigurationIssuesReporterTest {
  @get:Rule
  var projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  private val reporter = CxxConfigurationIssuesReporter()

  @Test
  fun testWithSingleModule() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->

      val syncIssue = setUpMockSyncIssue("19.1.2")


      val messages = reporter.report(syncIssue, project.gradleModule(":app")!!, null)
      assertSize(1, messages)
      val notification = messages[0]

      assertEquals("Gradle Sync Issues", notification.group)
      assertEquals(
        "No version of NDK matched the requested version 19.1.2\n" +
          "<a href=\"install.ndk\">Install NDK '19.1.2' and sync project</a>\n" +
          "Affected Modules: app",
        notification.message
      )
      assertEquals(MessageType.WARNING, notification.type)

      val quickFixes = messages[0]!!.quickFixes
      assertSize(1 + 1 /* affected modules */, quickFixes)
      assertInstanceOf(quickFixes[0], InstallNdkHyperlink::class.java)

      assertEquals(
        listOf(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.INSTALL_NDK_HYPERLINK)
            .build()
        ),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION, messages)
      )
    }
  }

  @Test
  fun testWithCompositeBuild() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.COMPOSITE_BUILD)
    preparedProject.open { project ->

      val syncIssueOne = setUpMockSyncIssue("19.1.2")
      val syncIssueTwo = setUpMockSyncIssue("19.1.1")
      val syncIssueThree = setUpMockSyncIssue("19.1.2") // Intentional duplicate of syncIssueOne

      val moduleMap = listOf(
        syncIssueOne to project.gradleModule(":")!!,
        syncIssueTwo to project.gradleModule(":TestCompositeLib1")!!,
        syncIssueThree to project.gradleModule(":TestCompositeLib3")!!
      ).toMap(IdentityHashMap())

      val messages =
        reporter
          .reportAll(listOf(syncIssueOne, syncIssueTwo, syncIssueThree), moduleMap, mapOf())
          .filter { it.type == MessageType.WARNING }
      assertSize(1, messages)
      val notificationOne = messages[0]

      assertEquals("Gradle Sync Issues", notificationOne.group)
      assertEquals(
        "No version of NDK matched the requested version 19.1.2\n" +
          "<a href=\"install.ndk\">Install NDK '19.1.2' and sync project</a>\n" +
          "Affected Modules: TestCompositeLib1, TestCompositeLib3, project",
        notificationOne.message
      )

      val quickFixes = messages[0].quickFixes
      assertSize(1 + 1 /* affected modules */, quickFixes)
      assertInstanceOf(quickFixes[0], InstallNdkHyperlink::class.java)

      val resultSyncIssue = GradleSyncIssue
        .newBuilder()
        .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION)
        .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.INSTALL_NDK_HYPERLINK)
        .build()
      assertEquals(
        listOf(resultSyncIssue),
        SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION, messages)
      )
    }
  }

  private fun setUpMockSyncIssue(revision: String): IdeSyncIssue {
    return IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_ERROR,
      type = IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION,
      data = null,
      message = "No version of NDK matched the requested version $revision",
      multiLineMessage = null
    )
  }
}
