/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.runsGradleErrors

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.UpdatePluginHyperlink
import com.android.tools.idea.gradle.project.sync.issues.OutOfDateThirdPartyPluginIssueReporter
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueUsageReporter
import com.android.tools.idea.gradle.project.sync.issues.TestSyncIssueUsageReporter
import com.android.tools.idea.project.messages.MessageType
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.testFramework.UsefulTestCase.assertSize
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OutOfDateThirdPartyPluginIssueReporterTest {
  private lateinit var reporter: OutOfDateThirdPartyPluginIssueReporter
  private lateinit var usageReporter: TestSyncIssueUsageReporter

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    reporter = OutOfDateThirdPartyPluginIssueReporter()
    usageReporter = TestSyncIssueUsageReporter()
  }

  @Test
  fun testReporterEmitsCorrectLinks() {
    projectRule.loadProject(SIMPLE_APPLICATION)

    val syncIssue = setUpMockSyncIssue("pluginName", "pluginGroup", "2.3.4", listOf("path/one", "path/two"))

    val module = projectRule.project.findAppModule()
    val messages = reporter.report(syncIssue, module, null)

    assertSize(1, messages)
    val notification = messages[0].syncMessage

    assertThat(notification.group).isEqualTo("Gradle Sync Issues")
    assertThat(notification.message).isEqualTo(
      "This is some message:\npath/one\npath/two\n" +
      "<a href=\"update.plugins\">Update plugins</a>\n" +
      "Affected Modules: app")

    assertThat(notification.type).isEqualTo(MessageType.WARNING)

    val quickFixes = notification.quickFixes
    assertSize(1 + 1 /* affected modules */, quickFixes)
    assertThat(quickFixes[0]).isInstanceOf(UpdatePluginHyperlink::class.java)
    val pluginHyperlink = quickFixes[0] as UpdatePluginHyperlink
    assertSize(1, pluginHyperlink.pluginToVersionMap.values)
    val entry = pluginHyperlink.pluginToVersionMap.entries.first()
    assertThat(entry.key.name).isEqualTo("pluginName")
    assertThat(entry.key.group).isEqualTo("pluginGroup")
    assertThat(entry.value).isEqualTo("2.3.4")
    assertThat(messages[0].affectedModules).isEqualTo(listOf(module))

    assertThat(
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD, messages.map { it.syncMessage }))
      .isEqualTo(listOf(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.UPDATE_PLUGIN_HYPERLINK)
          .build())
      )
  }

  private fun setUpMockSyncIssue(name: String, group: String, minVersion: String, paths: List<String>): IdeSyncIssue = object : IdeSyncIssue {
    override val severity: Int = IdeSyncIssue.SEVERITY_ERROR

    override val type: Int = IdeSyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD

    override val data: String = listOf("Some Plugin", group, name, minVersion, paths.joinToString(",", "[", "]")).joinToString(";")

    override val message: String = "This is some message"

    override val multiLineMessage: List<String> = listOf(message)
  }
}
