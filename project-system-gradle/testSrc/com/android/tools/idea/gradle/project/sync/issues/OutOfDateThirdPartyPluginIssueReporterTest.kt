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
import com.android.tools.idea.gradle.project.sync.hyperlink.UpdatePluginHyperlink
import com.android.tools.idea.project.messages.MessageType
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import org.junit.Test

class OutOfDateThirdPartyPluginIssueReporterTest : AndroidGradleTestCase() {
  private lateinit var reporter: OutOfDateThirdPartyPluginIssueReporter
  private lateinit var usageReporter: TestSyncIssueUsageReporter

  override fun setUp() {
    super.setUp()

    reporter = OutOfDateThirdPartyPluginIssueReporter()
    usageReporter = TestSyncIssueUsageReporter()
  }

  @Test
  fun testReporterEmitsCorrectLinks() {
    loadSimpleApplication()

    val syncIssue = setUpMockSyncIssue("pluginName", "pluginGroup", "2.3.4", listOf("path/one", "path/two"))

    val messages = reporter.report(syncIssue, getModule("app"), null)

    assertSize(1, messages)
    val notification = messages[0]

    assertEquals("Gradle Sync Issues", notification.group)
    assertEquals("This is some message:\npath/one\npath/two\n" +
                   "<a href=\"update.plugins\">Update plugins</a>\n" +
                   "Affected Modules: app",
                 notification.message)
    assertEquals(MessageType.WARNING, notification.type)

    val quickFixes = messages[0].quickFixes
    assertSize(1 + 1 /* affected modules */, quickFixes)
    assertInstanceOf(quickFixes[0], UpdatePluginHyperlink::class.java)
    val pluginHyperlink = quickFixes[0] as UpdatePluginHyperlink
    assertSize(1, pluginHyperlink.pluginToVersionMap.values)
    val entry = pluginHyperlink.pluginToVersionMap.entries.first()
    assertEquals("pluginName", entry.key.name)
    assertEquals("pluginGroup", entry.key.group)
    assertEquals("2.3.4", entry.value)

    assertEquals(
      listOf(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.UPDATE_PLUGIN_HYPERLINK)
          .build()),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD, messages))
  }

  private fun setUpMockSyncIssue(name: String, group: String, minVersion: String, paths: List<String>): IdeSyncIssue = object : IdeSyncIssue {
    override val severity: Int = IdeSyncIssue.SEVERITY_ERROR

    override val type: Int = IdeSyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD

    override val data: String = listOf("Some Plugin", group, name, minVersion, paths.joinToString(",", "[", "]")).joinToString(";")

    override val message: String = "This is some message"

    override val multiLineMessage: List<String> = listOf(message)
  }
}
