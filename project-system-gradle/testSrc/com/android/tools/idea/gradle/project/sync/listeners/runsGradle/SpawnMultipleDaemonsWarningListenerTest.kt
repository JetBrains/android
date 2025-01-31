/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.listeners.runsGradle

import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.JdkConstants
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.replaceService
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class SpawnMultipleDaemonsWarningListenerTest : AndroidGradleTestCase() {

  private val notifications = mutableListOf<Notification>()

  override fun setUp() {
    super.setUp()
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications.add(notification)
      }
    })
  }

  fun `test Given undefined jdkFromJavaHomePath When sync finished Then MultipleGradleDaemons warning is displayed`() {
    val jdkFromJavaHomePath: String? = null
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, testRootDisposable)
    loadSimpleApplication()

    notifications
      .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run {
        Truth.assertThat(this).hasSize(1)
        assertEquals(createWarningMessageMultipleGradleDaemons(jdkFromJavaHomePath), first().content)
      }
  }

  fun `test Given project using Daemon Jvm Criteria When sync finished Then MultipleGradleDaemons warning isn't displayed`() {
    val jdkFromJavaHomePath = "/test/jdk/path"
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, testRootDisposable)

    createDaemonJvmPropertiesFile(JdkConstants.JDK_EMBEDDED_VERSION.toString())
    loadSimpleApplication()

    notifications
      .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run { assertTrue(isEmpty()) }
  }

  fun `test Given project using invalid Daemon Jvm Criteria When gradle sync fails Then MultipleGradleDaemons warning isn't displayed`() {
    val jdkFromJavaHomePath = "/test/jdk/path"
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, testRootDisposable)

    createDaemonJvmPropertiesFile("invalid")
    loadProjectAndExpectSyncError(TestProjectPaths.SIMPLE_APPLICATION)

    notifications
      .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run { assertTrue(isEmpty()) }
  }

  fun `test Given different jdkFromJavaHomePath and jdkPath When sync finished Then MultipleGradleDaemons warning is displayed`() {
    val jdkFromJavaHomePath = "/test/jdk/path"
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, testRootDisposable)

    loadSimpleApplication()

    notifications
      .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run {
        Truth.assertThat(this).hasSize(1)
        assertEquals(createWarningMessageMultipleGradleDaemons(jdkFromJavaHomePath), first().content)
      }
  }

  fun `test Given same jdkFromJavaHomePath and jdkPath When sync finished Then MultipleGradleDaemons warning isn't displayed`() {
    val jdkFromJavaHomePath = GradleInstallationManager.getInstance().getGradleJvmPath(project, project.basePath.orEmpty())
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, testRootDisposable)

    loadSimpleApplication()

    notifications
      .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run { assertTrue(isEmpty()) }
  }

  private fun createWarningMessageMultipleGradleDaemons(
    jdkFromJavaHomePath: String? = null
  ) = StringBuilder().apply {
    append(
      AndroidBundle.message("project.sync.warning.multiple.gradle.daemons.message",
                            project.name,
                            GradleInstallationManager.getInstance().getGradleJvmPath(project, project.basePath.orEmpty()) ?: "Undefined",
                            jdkFromJavaHomePath ?: "Undefined"
      )
    )
    append("<br>", OpenUrlHyperlink(AndroidBundle.message("project.sync.warning.multiple.gradle.daemons.url"), "More info...").toHtml())
    append("<br>", SelectJdkFromFileSystemHyperlink.Companion.create(project, project.basePath)?.toHtml())
    append("<br>", DoNotShowJdkHomeWarningAgainHyperlink().toHtml())
  }.toString()

  private fun createDaemonJvmPropertiesFile(version: String?) {
    VfsTestUtil.createFile(project.guessProjectDir()!!, "gradle/gradle-daemon-jvm.properties", version?.let { "toolchainVersion=$version" }.orEmpty())
  }
}