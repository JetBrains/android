/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.service.notification.GradleJvmNotificationExtension.Companion.getInvalidJdkReason
import com.android.tools.idea.gradle.service.notification.UseJdkAsProjectJdkListener.Companion.baseId
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests.overrideJdkTo8
import com.android.tools.idea.testing.AndroidGradleTests.restoreJdk
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.replaceService
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import javax.swing.event.HyperlinkEvent

class GradleJvmNotificationExtensionTest {
  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  private val gradleJvmExtension = GradleJvmNotificationExtension()

  @Before
  fun setUp() {
    gradleProjectRule.loadProject(SIMPLE_APPLICATION)
  }

  @After
  fun tearDown() {
    restoreJdk()
  }

  @Test
  fun `customize with expected message and getJdk same as embedded`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    val embeddedPath = IdeSdks.getInstance().embeddedJdkPath.toAbsolutePath().toString()
    val expectedMessage = originalMessage + "<a href=\"${baseId()}.embedded\">Use Embedded JDK ($embeddedPath)</a>\n" +
                          "<a href=\"${OpenProjectJdkLocationListener.ID}\">Change JDK location</a>\n"
    assertThat(notificationData.registeredListenerIds).isEmpty()

    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, null)
    assertThat(notificationData.message).isEqualTo(expectedMessage)
    val newListeners = notificationData.registeredListenerIds
    assertThat(newListeners).contains("${baseId()}.embedded")
    assertThat(newListeners).contains(OpenProjectJdkLocationListener.ID)
  }

  @Test
  fun `customize with expected message and getJdk different to embedded`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    overrideJdkTo8()
    val ideSdks = IdeSdks.getInstance()
    val jdk = ideSdks.jdk!!
    val embeddedPath = ideSdks.embeddedJdkPath.toAbsolutePath().toString()
    val expectedMessage = originalMessage + "<a href=\"${baseId()}.embedded\">Use Embedded JDK ($embeddedPath)</a>\n" +
                          "<a href=\"${baseId()}\">Use JDK ${jdk.name} (${jdk.homePath})</a>\n" +
                          "<a href=\"${OpenProjectJdkLocationListener.ID}\">Change JDK location</a>\n"
    assertThat(notificationData.registeredListenerIds).isEmpty()

    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, null)
    assertThat(notificationData.message).isEqualTo(expectedMessage)
    val newListeners = notificationData.registeredListenerIds
    assertThat(newListeners).contains("${baseId()}.embedded")
    assertThat(newListeners).contains(baseId())
    assertThat(newListeners).contains(OpenProjectJdkLocationListener.ID)
  }

  @Test
  fun `customize with expected message and quickfix already there`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n" +
                          "<a href=\"${baseId()}.embedded\">Customized embedded</a>\n" +
                          "<a href=\"${baseId()}\">Customized</a>\n" +
                          "<a href=\"${OpenProjectJdkLocationListener.ID}\">Customized location</a>\n"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    notificationData.setListener("${baseId()}.embedded") { _: Notification, _: HyperlinkEvent -> }
    notificationData.setListener(baseId()) { _: Notification, _: HyperlinkEvent -> }
    notificationData.setListener(OpenProjectJdkLocationListener.ID) { _: Notification, _: HyperlinkEvent -> }

    overrideJdkTo8()
    assertThat(notificationData.registeredListenerIds).hasSize(3)
    val spyData = spy(notificationData)

    gradleJvmExtension.customize(spyData, gradleProjectRule.project, null)
    // Should not add new quickfixes nor modify the message if they are already there
    verify(spyData, never()).setListener(Mockito.any(), Mockito.any())
    assertThat(notificationData.message).isEqualTo(originalMessage)
    assertThat(notificationData.registeredListenerIds).hasSize(3)
  }

  @Test
  fun `customize without expected message`() {
    val originalMessage = "Some other message."
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    assertThat(notificationData.registeredListenerIds).hasSize(0)
    val spyData = spy(notificationData)

    gradleJvmExtension.customize(spyData, gradleProjectRule.project, null)
    // Should not add new quickfixes nor modify the message if the error message does not start with "Invalid Gradle JDK..."
    verify(spyData, never()).setListener(Mockito.any(), Mockito.any())
    assertThat(notificationData.message).isEqualTo(originalMessage)
    assertThat(notificationData.registeredListenerIds).hasSize(0)
  }

  @Test
  fun `customize with invalid JDK reason`() {
    val originalMessage = "Invalid Gradle JDK configuration found."
    val invalidReason = "Generating invalid JDK reason for Test."
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    val embeddedPath = IdeSdks.getInstance().embeddedJdkPath.toAbsolutePath().toString()
    val expectedMessage = "$originalMessage $invalidReason\n" +
                          "<a href=\"${baseId()}.embedded\">Use Embedded JDK ($embeddedPath)</a>\n" +
                          "<a href=\"${OpenProjectJdkLocationListener.ID}\">Change JDK location</a>\n"
    assertThat(notificationData.registeredListenerIds).isEmpty()

    val spyIdeSdks = spy(IdeSdks.getInstance())
    Mockito.doReturn(invalidReason).whenever(spyIdeSdks).generateInvalidJdkReason(Mockito.any())
    ApplicationManager.getApplication().replaceService(IdeSdks::class.java, spyIdeSdks, gradleProjectRule.fixture.projectDisposable)
    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, null)
    assertThat(notificationData.message).isEqualTo(expectedMessage)
    val newListeners = notificationData.registeredListenerIds
    assertThat(newListeners).contains("${baseId()}.embedded")
    assertThat(newListeners).contains(OpenProjectJdkLocationListener.ID)
  }

  @Test
  fun `null path generates invalid JDK error`() {
    val mockJdk = Mockito.mock(Sdk::class.java)
    whenever(mockJdk.homePath).thenReturn(null)
    val spyProjectJdkTable = spy(ProjectJdkTable.getInstance())
    whenever(spyProjectJdkTable.findJdk(MockitoKt.any(), MockitoKt.any())).thenReturn(mockJdk)
    val mockGradleManager = Mockito.mock(GradleInstallationManager::class.java)
    whenever(mockGradleManager.getGradleJvmPath(MockitoKt.any(), MockitoKt.any())).thenReturn(null)

    val project = gradleProjectRule.project
    ApplicationManager.getApplication().replaceService(GradleInstallationManager::class.java, mockGradleManager, project)
    ApplicationManager.getApplication().replaceService(ProjectJdkTable::class.java, spyProjectJdkTable, project)

    val actualMessage = getInvalidJdkReason(gradleProjectRule.project)
    assertThat(actualMessage).isNotNull()
  }

  @Test
  fun `invalid JDK path generates error`() {
    val mockGradleManager = Mockito.mock(GradleInstallationManager::class.java)
    whenever(mockGradleManager.getGradleJvmPath(MockitoKt.any(), MockitoKt.any())).thenReturn("/path/to/invalid/jdk/")

    val project = gradleProjectRule.project
    ApplicationManager.getApplication().replaceService(GradleInstallationManager::class.java, mockGradleManager, project)

    val actualMessage = getInvalidJdkReason(project)
    assertThat(actualMessage).isNotNull()
  }

  @Test
  fun `valid path does not generate error`() {
    val jdk = IdeSdks.getInstance().jdk
    assertThat(jdk).isNotNull()
    val jdkPath = jdk!!.homePath
    assertThat(jdkPath).isNotEmpty()

    val mockGradleManager = Mockito.mock(GradleInstallationManager::class.java)
    whenever(mockGradleManager.getGradleJvmPath(MockitoKt.any(), MockitoKt.any())).thenReturn(jdkPath)

    val project = gradleProjectRule.project
    ApplicationManager.getApplication().replaceService(GradleInstallationManager::class.java, mockGradleManager, project)
    val actualMessage = getInvalidJdkReason(project)
    assertThat(actualMessage).isNull()
  }
}