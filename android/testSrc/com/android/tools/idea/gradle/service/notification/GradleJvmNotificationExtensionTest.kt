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

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC
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

  @Test
  fun `customize with expected message`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    val expectedMessage = originalMessage + "<a href=\"${UseProjectJdkAsGradleJvmListener.ID}\">Use JDK from project structure</a>"
    assertThat(notificationData.registeredListenerIds).isEmpty()

    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, null)
    assertThat(notificationData.message).isEqualTo(expectedMessage)
    val newListeners = notificationData.registeredListenerIds
    assertThat(newListeners).contains(UseProjectJdkAsGradleJvmListener.ID)
  }

  @Test
  fun `customize with expected message and quickfix already`() {
    val originalMessage = "Invalid Gradle JDK configuration found.\n<a href=\"${UseProjectJdkAsGradleJvmListener.ID}\">Use JDK from project structure</a>"
    val notificationData = NotificationData("Test error title", originalMessage, ERROR, PROJECT_SYNC)
    notificationData.setListener(UseProjectJdkAsGradleJvmListener.ID, NotificationListener { _: Notification, _: HyperlinkEvent -> })
    assertThat(notificationData.registeredListenerIds).hasSize(1)
    val spyData = spy(notificationData)

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