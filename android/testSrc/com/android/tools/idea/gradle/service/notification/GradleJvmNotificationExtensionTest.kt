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

import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import javax.swing.event.HyperlinkEvent

class GradleJvmNotificationExtensionTest {
  private var gradleProjectSettings: GradleProjectSettings? = null

  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  private val gradleJvmExtension = GradleJvmNotificationExtension()

  @Before
  fun setUp() {
    gradleProjectRule.loadProject(SIMPLE_APPLICATION)
    gradleProjectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(gradleProjectRule.project)
    assertThat(gradleProjectSettings).isNotNull()
  }

  @Test
  fun `customize with expected message`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    val jdk = IdeSdks.getInstance().jdk!!
    val expectedMessage = originalMessage + "<a href=\"${UseJdkAsProjectJdkListener.ID}\">Use JDK ${jdk.name} (${jdk.homePath})</a>"
    assertThat(notificationData.registeredListenerIds).isEmpty()

    gradleProjectSettings!!.gradleJvm = USE_JAVA_HOME
    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, null)
    assertThat(notificationData.message).isEqualTo(expectedMessage)
    val newListeners = notificationData.registeredListenerIds
    assertThat(newListeners).contains(UseJdkAsProjectJdkListener.ID)
  }

  @Test
  fun `customize with expected message and quickfix already`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n<a href=\"${UseJdkAsProjectJdkListener.ID}\">Customized message</a>"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    notificationData.setListener(UseJdkAsProjectJdkListener.ID) { _: Notification, _: HyperlinkEvent -> }
    assertThat(notificationData.registeredListenerIds).hasSize(1)
    val spyData = spy(notificationData)

    gradleProjectSettings!!.gradleJvm = USE_JAVA_HOME
    gradleJvmExtension.customize(spyData, gradleProjectRule.project, null)
    verify(spyData, never()).setListener(Mockito.any(), Mockito.any())
    assertThat(notificationData.message).isEqualTo(originalMessage)
    assertThat(notificationData.registeredListenerIds).hasSize(1)
  }

  @Test
  fun `customize with expected message and USE_PROJECT_JDK already`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    val expectedMessage = originalMessage + "<a href=\"${OpenProjectJdkLocationListener.ID}\">Change JDK location</a>"
    assertThat(notificationData.registeredListenerIds).isEmpty()

    gradleProjectSettings!!.gradleJvm = USE_PROJECT_JDK
    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, null)
    assertThat(notificationData.message).isEqualTo(expectedMessage)
    val newListeners = notificationData.registeredListenerIds
    assertThat(newListeners).contains(OpenProjectJdkLocationListener.ID)
  }


  @Test
  fun `customize with expected message, USE_PROJECT_JDK and quickfix already`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    notificationData.setListener(OpenProjectJdkLocationListener.ID) { _: Notification, _: HyperlinkEvent -> }
    assertThat(notificationData.registeredListenerIds).hasSize(1)
    val spyData = spy(notificationData)

    gradleProjectSettings!!.gradleJvm = USE_PROJECT_JDK
    gradleJvmExtension.customize(spyData, gradleProjectRule.project, null)
    verify(spyData, never()).setListener(Mockito.any(), Mockito.any())
    assertThat(notificationData.message).isEqualTo(originalMessage)
    assertThat(notificationData.registeredListenerIds).hasSize(1)
  }

  @Test
  fun `customize without expected message`() {
    val originalMessage = "Some other message."
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    assertThat(notificationData.registeredListenerIds).hasSize(0)
    val spyData = spy(notificationData)

    gradleJvmExtension.customize(spyData, gradleProjectRule.project, null)
    verify(spyData, never()).setListener(Mockito.any(), Mockito.any())
    assertThat(notificationData.message).isEqualTo(originalMessage)
    assertThat(notificationData.registeredListenerIds).hasSize(0)
  }
}