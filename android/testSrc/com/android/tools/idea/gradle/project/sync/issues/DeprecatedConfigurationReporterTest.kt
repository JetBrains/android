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

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.INFO
import com.intellij.openapi.module.Module
import com.intellij.testFramework.HeavyPlatformTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class DeprecatedConfigurationReporterTest : HeavyPlatformTestCase() {
  private lateinit var syncIssue1: IdeSyncIssue
  private lateinit var syncIssue2: IdeSyncIssue
  private lateinit var module1: Module
  private lateinit var module2: Module
  private lateinit var messageStub: GradleSyncMessagesStub
  private lateinit var reporter: DeprecatedConfigurationReporter
  private lateinit var usageReporter: TestSyncIssueUsageReporter

  override fun setUp() {
    super.setUp()
    messageStub = GradleSyncMessagesStub.replaceSyncMessagesService(project)
    messageStub.removeAllMessages()
    reporter = DeprecatedConfigurationReporter()
    syncIssue1 = mock(IdeSyncIssue::class.java)
    syncIssue2 = mock(IdeSyncIssue::class.java)
    module1 = createModule("app")
    module2 = createModule("lib")
    usageReporter = TestSyncIssueUsageReporter()

    `when`(syncIssue1.type).thenReturn(IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION)
    `when`(syncIssue2.type).thenReturn(IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION)
  }

  @Test
  fun testDeduplicationInSameModule() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key")
    `when`(syncIssue1.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module1), mapOf(), usageReporter)

    val messages = messageStub.notifications
    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app"))
    assertThat(message.notificationCategory, equalTo(INFO))

    assertThat(GradleSyncMessagesStub.getInstance(project).errorCount, equalTo(0))

    assertEquals(
      listOf(GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()),
      usageReporter.collectedIssue)
  }

  @Test
  fun testNoDeduplicationInSameModule() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key1")
    `when`(syncIssue1.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2), mapOf(), usageReporter)

    val messages = messageStub.notifications
    assertSize(2, messages)
    var message = messages[0]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app"))
    assertThat(message.notificationCategory, equalTo(INFO))

    message = messages[1]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: lib"))
    assertThat(message.notificationCategory, equalTo(INFO))

    assertThat(GradleSyncMessagesStub.getInstance(project).errorCount, equalTo(0))

    assertEquals(
      listOf(
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build(),
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()
      ),
      usageReporter.collectedIssue)
  }

  @Test
  fun testDeduplicationAcrossModules() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key")
    `when`(syncIssue1.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2), mapOf(), usageReporter)

    val messages = messageStub.notifications
    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app, lib"))
    assertThat(message.notificationCategory, equalTo(INFO))

    assertThat(GradleSyncMessagesStub.getInstance(project).errorCount, equalTo(0))

    assertEquals(
      listOf(GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()),
      usageReporter.collectedIssue)
  }

  @Test
  fun testNoDeduplicationAcrossModules() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key1")
    `when`(syncIssue1.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2), mapOf(), usageReporter)

    val messages = messageStub.notifications
    assertSize(2, messages)
    var message = messages[0]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: app"))
    assertThat(message.notificationCategory, equalTo(INFO))

    message = messages[1]
    assertNotNull(message)
    assertThat(message.message, equalTo("Warning message!\nAffected Modules: lib"))
    assertThat(message.notificationCategory, equalTo(INFO))

    assertThat(messageStub.fakeErrorCount, equalTo(0))

    assertEquals(
      listOf(
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build(),
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()
      ),
      usageReporter.collectedIssue)
  }

  @Test
  fun testDeduplicationHandlesErrors() {
    `when`(syncIssue1.message).thenReturn("Error message!")
    `when`(syncIssue1.data).thenReturn("key")
    `when`(syncIssue1.severity).thenReturn(IdeSyncIssue.SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Error message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(IdeSyncIssue.SEVERITY_ERROR)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2), mapOf(), usageReporter)

    val messages = messageStub.notifications
    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertThat(message.message, equalTo("Error message!\nAffected Modules: app, lib"))
    assertThat(message.notificationCategory, equalTo(WARNING))

    assertThat(messageStub.fakeErrorCount, equalTo(0))

    assertEquals(
      listOf(GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION).build()),
      usageReporter.collectedIssue)
  }
}
