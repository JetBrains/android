/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.mockStatic
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.sync.hyperlink.SuppressUnsupportedSdkVersionHyperlink
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.project.messages.MessageType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.gradleModule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.testFramework.UsefulTestCase.assertSize
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations


@RunsInEdt
class CompileSdkVersionTooHighReporterTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private val reporter = CompileSdkVersionTooHighReporter()

  @Before
  fun setUp(){
    MockitoAnnotations.initMocks(this)
  }

  @After
  fun tearDown() {
    StudioFlags.ANDROID_SDK_AND_IDE_COMPATIBILITY_RULES.clearOverride()
  }

  @Test
  fun `test suppress quick fix message extracted from sync data`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.APP_WITH_BUILD_FEATURES_ENABLED)
    val syncMessage = "Some random text"
    val syncData = "android.suppressUnsupportedCompileSdk=UpsideDownCake"

    preparedProject.open { project ->
      val syncIssue = setUpMockSyncIssue(syncMessage, syncData)
      val messages = reporter.report(syncIssue, project.gradleModule(":app")!!, null)

      assertSize(1, messages)
      val notification = messages[0]
      assertEquals("Gradle Sync Issues", notification.group)
      assertEquals(
        """
          Some random text
          <a href="android.suppressUnsupportedCompileSdk">Update Gradle property to suppress warning</a>
          Affected Modules: app
        """.trimIndent(),
        notification.message
      )
      assertEquals(MessageType.INFO, notification.type)

      val quickFixes = messages[0].quickFixes
      assertSize(1 + 1 /* affected modules */, quickFixes)
      assertInstanceOf(quickFixes[0], SuppressUnsupportedSdkVersionHyperlink::class.java)
      assertEquals("android.suppressUnsupportedCompileSdk=UpsideDownCake", (quickFixes[0] as SuppressUnsupportedSdkVersionHyperlink).gradleProperty)
    }
  }

  @Test
  fun `test suppress quick fix message when extracted from sync message`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.APP_WITH_BUILD_FEATURES_ENABLED)
    val syncMessage = "Some random text android.suppressUnsupportedCompileSdk=UpsideDownCake with some more text here"
    val syncData = null

    preparedProject.open { project ->
      val pluginInfo = mock<AndroidPluginInfo>()
      whenever(pluginInfo.pluginVersion).thenReturn(AgpVersion.parse("8.2.0-alpha06"))
      val mock = mockStatic<AndroidPluginInfo>()
      mock.whenever<Any?> { AndroidPluginInfo.find(project) }.thenReturn(pluginInfo)

      val syncIssue = setUpMockSyncIssue(syncMessage, syncData)
      val messages = reporter.report(syncIssue, project.gradleModule(":app")!!, null)

      assertSize(1, messages)
      val notification = messages[0]
      assertEquals("Gradle Sync Issues", notification.group)
      assertEquals(
        """
          Some random text android.suppressUnsupportedCompileSdk=UpsideDownCake with some more text here
          <a href="android.suppressUnsupportedCompileSdk">Update Gradle property to suppress warning</a>
          Affected Modules: app
        """.trimIndent(),
        notification.message
      )
      assertEquals(MessageType.INFO, notification.type)

      val quickFixes = messages[0].quickFixes
      assertSize(1 + 1 /* affected modules */, quickFixes)
      assertInstanceOf(quickFixes[0], SuppressUnsupportedSdkVersionHyperlink::class.java)
      assertEquals("android.suppressUnsupportedCompileSdk=UpsideDownCake", (quickFixes[0] as SuppressUnsupportedSdkVersionHyperlink).gradleProperty)
      mock.close()
    }
  }

  @Test
  fun `test suppress quick fix message when extracting from sync message fails`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.APP_WITH_BUILD_FEATURES_ENABLED)
    val syncMessage = "Some random text only and nothing to extract"
    val syncData = null

    preparedProject.open { project ->
      val pluginInfo = mock<AndroidPluginInfo>()
      whenever(pluginInfo.pluginVersion).thenReturn(AgpVersion.parse("8.2.0-alpha06"))
      val mock = mockStatic<AndroidPluginInfo>()
      mock.whenever<Any?> { AndroidPluginInfo.find(project) }.thenReturn(pluginInfo)

      val syncIssue = setUpMockSyncIssue(syncMessage, syncData)
      val messages = reporter.report(syncIssue, project.gradleModule(":app")!!, null)

      assertSize(1, messages)
      val notification = messages[0]
      assertEquals("Gradle Sync Issues", notification.group)
      assertEquals(
        """
          Some random text only and nothing to extract
          Affected Modules: app
        """.trimIndent(),
        notification.message
      )
      assertEquals(MessageType.INFO, notification.type)

      val quickFixes = messages[0].quickFixes
      assertSize(0 + 1 /* affected modules */, quickFixes)
      mock.close()
    }
  }

  private fun setUpMockSyncIssue(syncMessage: String, syncData: String?): IdeSyncIssue {
    return IdeSyncIssueImpl(
      data = syncData,
      severity = IdeSyncIssue.SEVERITY_WARNING,
      message = syncMessage,
      type = IdeSyncIssue.TYPE_COMPILE_SDK_VERSION_TOO_HIGH,
      multiLineMessage = null
    )
  }
}