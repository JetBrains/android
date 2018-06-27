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

import com.android.builder.model.SyncIssue
import com.android.builder.model.SyncIssue.SEVERITY_ERROR
import com.android.builder.model.SyncIssue.SEVERITY_WARNING
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.TestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class DeprecatedConfigurationReporterTest : AndroidGradleTestCase() {

  private lateinit var syncIssue1: SyncIssue
  private lateinit var syncIssue2: SyncIssue
  private lateinit var module1: Module
  private lateinit var module2: Module
  private lateinit var messageStub: GradleSyncMessagesStub
  private lateinit var reporter: DeprecatedConfigurationReporter
  private lateinit var mockFile: VirtualFile

  override fun setUp() {
    super.setUp()
    messageStub = GradleSyncMessagesStub.replaceSyncMessagesService(project)
    messageStub.clearReportedMessages()
    reporter = DeprecatedConfigurationReporter()
    reporter = DeprecatedConfigurationReporter()
    syncIssue1 = mock(SyncIssue::class.java)
    syncIssue2 = mock(SyncIssue::class.java)
    module1 = mock(Module::class.java)
    module2 = mock(Module::class.java)
    mockFile = mock(VirtualFile::class.java)

    `when`(module1.name).thenReturn("app")
    `when`(module1.project).thenReturn(project)
    `when`(module2.name).thenReturn("lib")
    `when`(module2.project).thenReturn(project)
    `when`(mockFile.path).thenReturn("file/path")
  }

  @Test
  fun testDeduplicationInSameModule() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key")
    `when`(syncIssue1.severity).thenReturn(SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module1), mapOf(module1 to mockFile))

    val messages = messageStub.notifications
    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertSize(1, message.registeredListenerIds)
    assertThat(message.message, equalTo("Warning message!<br>Affected Modules: <a href=\"openFile:file/path\">app</a>"))
    assertThat(message.notificationCategory, equalTo(WARNING))

    assertThat(GradleSyncMessagesStub.getInstance(project).errorCount, equalTo(0))
  }

  @Test
  fun testNoDeduplicationInSameModule() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key1")
    `when`(syncIssue1.severity).thenReturn(SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2),
                       mapOf(module1 to mockFile))

    val messages = messageStub.notifications
    assertSize(2, messages)
    var message = messages[0]
    assertNotNull(message)
    assertSize(1, message.registeredListenerIds)
    assertThat(message.message, equalTo("Warning message!<br>Affected Modules: <a href=\"openFile:file/path\">app</a>"))
    assertThat(message.notificationCategory, equalTo(WARNING))

    message = messages[1]
    assertNotNull(message)
    assertSize(0, message.registeredListenerIds)
    assertThat(message.message, equalTo("Warning message!<br>Affected Modules: lib"))
    assertThat(message.notificationCategory, equalTo(WARNING))

    assertThat(GradleSyncMessagesStub.getInstance(project).errorCount, equalTo(0))
  }

  @Test
  fun testDeduplicationAcrossModules() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key")
    `when`(syncIssue1.severity).thenReturn(SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2),
                       mapOf(module1 to mockFile, module2 to mockFile))

    val messages = messageStub.notifications
    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertSize(1, message.registeredListenerIds)
    assertThat(message.message, equalTo("Warning message!<br>Affected Modules: " +
                                        "<a href=\"openFile:file/path\">app</a>, <a href=\"openFile:file/path\">lib</a>"))
    assertThat(message.notificationCategory, equalTo(WARNING))

    assertThat(GradleSyncMessagesStub.getInstance(project).errorCount, equalTo(0))
  }

  @Test
  fun testNoDeduplicationAcrossModules() {
    `when`(syncIssue1.message).thenReturn("Warning message!")
    `when`(syncIssue1.data).thenReturn("key1")
    `when`(syncIssue1.severity).thenReturn(SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Warning message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(SEVERITY_WARNING)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2),
                       mapOf(module1 to mockFile))

    val messages = messageStub.notifications
    assertSize(2, messages)
    var message = messages[0]
    assertNotNull(message)
    assertSize(1, message.registeredListenerIds)
    assertThat(message.message, equalTo("Warning message!<br>Affected Modules: <a href=\"openFile:file/path\">app</a>"))
    assertThat(message.notificationCategory, equalTo(WARNING))

    message = messages[1]
    assertNotNull(message)
    assertSize(0, message.registeredListenerIds)
    assertThat(message.message, equalTo("Warning message!<br>Affected Modules: lib"))
    assertThat(message.notificationCategory, equalTo(WARNING))

    assertThat(messageStub.fakeErrorCount, equalTo(0))
  }

  @Test
  fun testDeduplicationHandlesErrors() {
    `when`(syncIssue1.message).thenReturn("Error message!")
    `when`(syncIssue1.data).thenReturn("key")
    `when`(syncIssue1.severity).thenReturn(SEVERITY_WARNING)
    `when`(syncIssue2.message).thenReturn("Error message!")
    `when`(syncIssue2.data).thenReturn("key")
    `when`(syncIssue2.severity).thenReturn(SEVERITY_ERROR)

    reporter.reportAll(listOf(syncIssue1, syncIssue2), mapOf(syncIssue1 to module1, syncIssue2 to module2),
                       mapOf(module1 to mockFile, module2 to mockFile))

    val messages = messageStub.notifications
    assertSize(1, messages)
    val message = messages[0]
    assertNotNull(message)

    assertSize(1, message.registeredListenerIds)
    assertThat(message.message, equalTo("Error message!<br>Affected Modules: " +
                                        "<a href=\"openFile:file/path\">app</a>, <a href=\"openFile:file/path\">lib</a>"))
    assertThat(message.notificationCategory, equalTo(ERROR))

    assertThat(messageStub.fakeErrorCount, equalTo(1))
  }
}