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

import com.android.tools.idea.gradle.fixtures.createDaemonJvmPropertiesFile
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JdkConstants
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.replaceService
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class SpawnMultipleDaemonsWarningListenerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private val notifications = mutableListOf<Notification>()

  private val listener = object : Notifications {
    override fun notify(notification: Notification) {
      notifications.add(notification)
    }
  }

  private fun PreparedTestProject.openWithListener(
    updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions = { it },
    body: PreparedTestProject.Context.(Project) -> Unit
  ) = open(updateOptions = { updateOptions(it.copy(subscribe = { bus -> bus.subscribe(Notifications.TOPIC, listener)}))}, body)

  private fun assertSyncFailed(project: Project) {
    assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult().isSuccessful).isFalse()
  }

  @Test
  fun `test Given undefined jdkFromJavaHomePath When sync finished Then MultipleGradleDaemons warning is displayed`() {
    val jdkFromJavaHomePath: String? = null
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, projectRule.testRootDisposable)
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.openWithListener { project ->
      notifications
        .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
        .run {
          assertThat(this).hasSize(1)
          assertThat(first().content).isEqualTo(createWarningMessageMultipleGradleDaemons(project, jdkFromJavaHomePath))
        }
    }
  }

  @Test
  fun `test Given project using Daemon Jvm Criteria When sync finished Then MultipleGradleDaemons warning isn't displayed`() {
    val jdkFromJavaHomePath = "/test/jdk/path"
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, projectRule.testRootDisposable)

    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.createDaemonJvmPropertiesFile(JdkConstants.JDK_EMBEDDED_VERSION)
    preparedProject.openWithListener {
      notifications
        .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
        .run { assertThat(this).isEmpty() }
    }
  }

  @Test
  fun `test Given project using invalid Daemon Jvm Criteria When gradle sync fails Then MultipleGradleDaemons warning isn't displayed`() {
    val jdkFromJavaHomePath = "/test/jdk/path"
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, projectRule.testRootDisposable)

    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.createDaemonJvmPropertiesFile("invalid")
    preparedProject.openWithListener(updateOptions = { it -> it.copy(verifyOpened = ::assertSyncFailed) }) { project ->
      notifications
        .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
        .run { assertThat(this).isEmpty() }
    }
  }

  @Test
  fun `test Given different jdkFromJavaHomePath and jdkPath When sync finished Then MultipleGradleDaemons warning is displayed`() {
    val jdkFromJavaHomePath = "/test/jdk/path"
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, projectRule.testRootDisposable)

    projectRule
      .prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
      .openWithListener { project ->
        notifications
          .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
          .run {
            assertThat(this).hasSize(1)
            assertThat(first().content).isEqualTo(createWarningMessageMultipleGradleDaemons(project, jdkFromJavaHomePath))
          }
      }
  }

  @Test
  fun `test Given same jdkFromJavaHomePath and jdkPath When sync finished Then MultipleGradleDaemons warning isn't displayed`() {
    val defaultProject = ProjectManager.getInstance().defaultProject
    val jdkFromJavaHomePath = GradleInstallationManager.getInstance().getGradleJvmPath(defaultProject, defaultProject.basePath.orEmpty())
    val mockIdeSdks = Mockito.spy(IdeSdks.getInstance())
    whenever(mockIdeSdks.jdkFromJavaHome).thenReturn(jdkFromJavaHomePath)
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, mockIdeSdks, projectRule.testRootDisposable)

    projectRule
      .prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
      .openWithListener { project ->
        notifications
          .filter { GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
          .run { assertThat(this).isEmpty() }
    }
  }

  private fun createWarningMessageMultipleGradleDaemons(
    project: Project,
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
}