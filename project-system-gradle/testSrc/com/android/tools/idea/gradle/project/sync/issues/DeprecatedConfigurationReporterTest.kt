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
import com.android.tools.idea.project.messages.MessageType
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncIssueType
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.module.Module
import com.intellij.testFramework.HeavyPlatformTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.util.IdentityHashMap

class DeprecatedConfigurationReporterTest : HeavyPlatformTestCase() {
  private lateinit var module1: Module
  private lateinit var module2: Module
  private lateinit var reporter: DeprecatedConfigurationReporter
  private lateinit var usageReporter: TestSyncIssueUsageReporter

  override fun setUp() {
    super.setUp()
    reporter = DeprecatedConfigurationReporter()
    module1 = createModule("app")
    module2 = createModule("lib")
    usageReporter = TestSyncIssueUsageReporter()
  }

  @Test
  fun testDeduplicationInSameModule() {
    val syncIssue1 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    )
    val syncIssue2 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    )

    val messages = reporter.reportAll(
      listOf(syncIssue1, syncIssue2),
      listOf(syncIssue1 to module1, syncIssue2 to module1).toMap(IdentityHashMap()),
      mapOf()
    )

    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app"))
    assertThat(message.type, equalTo(MessageType.INFO))

    assertEquals(
      listOf(GradleSyncIssue.newBuilder().setType(GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION, messages))
  }

  @Test
  fun testNoDeduplicationInSameModule() {
    val syncIssue1 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key1",
      message = "Warning message!",
      multiLineMessage = null
    )
    val syncIssue2 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    )

    val messages = reporter.reportAll(
      listOf(syncIssue1, syncIssue2),
      listOf(syncIssue1 to module1, syncIssue2 to module2).toMap(IdentityHashMap()),
      mapOf()
    )

    assertSize(2, messages)
    var message = messages[0]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app"))
    assertThat(message.type, equalTo(MessageType.INFO))

    message = messages[1]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: lib"))
    assertThat(message.type, equalTo(MessageType.INFO))

    assertEquals(
      listOf(
        GradleSyncIssue.newBuilder().setType(GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build(),
        GradleSyncIssue.newBuilder().setType(GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()
      ),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION, messages))
  }

  @Test
  fun testDeduplicationAcrossModules() {
    val syncIssue1 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    )
    val syncIssue2 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    )

    val messages = reporter.reportAll(
      listOf(syncIssue1, syncIssue2),
      listOf(syncIssue1 to module1, syncIssue2 to module2).toMap(IdentityHashMap()),
      mapOf()
    )

    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app, lib"))
    assertThat(message.type, equalTo(MessageType.INFO))

    assertEquals(
      listOf(GradleSyncIssue.newBuilder().setType(GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION, messages))
  }

  @Test
  fun testNoDeduplicationAcrossModules() {
    val syncIssue1 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key1",
      message = "Warning message!",
      multiLineMessage = null
    )
    val syncIssue2 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Warning message!",
      multiLineMessage = null
    )

    val messages = reporter.reportAll(
      listOf(syncIssue1, syncIssue2),
      listOf(syncIssue1 to module1, syncIssue2 to module2).toMap(IdentityHashMap()),
      mapOf()
    )

    assertSize(2, messages)
    var message = messages[0]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app"))
    assertThat(message.type, equalTo(MessageType.INFO))

    message = messages[1]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: lib"))
    assertThat(message.type, equalTo(MessageType.INFO))

    assertEquals(
      listOf(
        GradleSyncIssue.newBuilder().setType(GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build(),
        GradleSyncIssue.newBuilder().setType(GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()
      ),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION, messages))
  }

  @Test
  fun testDeduplicationHandlesErrors() {
    val syncIssue1 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_WARNING,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Error message!",
      multiLineMessage = null
    )
    val syncIssue2 = IdeSyncIssueImpl(
      severity = IdeSyncIssue.SEVERITY_ERROR,
      type = IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION,
      data = "key",
      message = "Error message!",
      multiLineMessage = null
    )

    val messages = reporter.reportAll(
      listOf(syncIssue1, syncIssue2),
      listOf(syncIssue1 to module1, syncIssue2 to module2).toMap(IdentityHashMap()),
      mapOf()
    )

    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertThat(message.message, equalTo("Error message!\nAffected Modules: app, lib"))
    assertThat(message.type, equalTo(MessageType.WARNING))

    assertEquals(
      listOf(GradleSyncIssue.newBuilder().setType(GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION, messages))
  }
}
