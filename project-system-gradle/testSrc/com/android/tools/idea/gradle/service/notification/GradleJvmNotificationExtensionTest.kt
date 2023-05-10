/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.jdk.GradleJdkValidationManager
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.base.GradleJdkException
import com.android.tools.idea.gradle.service.notification.UseJdkAsProjectJdkListener.Companion.baseId
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests.overrideJdkTo8
import com.android.tools.idea.testing.AndroidGradleTests.restoreJdk
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC
import com.intellij.testFramework.replaceService
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyString
import kotlin.test.assertEquals

class GradleJvmNotificationExtensionTest {

  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    gradleProjectRule.loadProject(SIMPLE_APPLICATION)
  }

  @After
  fun tearDown() {
    restoreJdk()
  }

  private val invalidGradleJdkErrorText = GradleBundle.message("gradle.jvm.is.invalid")
  private val gradleJvmExtension = GradleJvmNotificationExtension()
  private val externalProjectPath by lazy {
    gradleProjectRule.project.basePath.orEmpty()
  }
  private val embeddedJdkPath by lazy {
    IdeSdks.getInstance().embeddedJdkPath.toString()
  }

  @Test
  fun `Given unexpected exception When customize notificationData Then no modification happened`() {
    val originalTitle = "Some other title"
    val originalMessage = "Some other message"
    val originalFilePath = "path/to/file.txt"
    val originalFileLine = 10
    val originalFileColumn = 2
    val originalIsBalloon = true
    val notificationData = NotificationData(
      originalTitle, originalMessage, ERROR, PROJECT_SYNC, originalFilePath, originalFileLine, originalFileColumn, originalIsBalloon
    )
    assertThat(notificationData.registeredListenerIds).isEmpty()
    mockGradleJdkException(originalMessage)
    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, externalProjectPath, null)

    notificationData.run {
      assertEquals(originalTitle, title)
      assertEquals(originalMessage, message)
      assertEquals(originalFilePath, filePath)
      assertEquals(originalFileLine, line)
      assertEquals(originalFileColumn, column)
      assertEquals(originalIsBalloon, isBalloonNotification)
    }
  }

  @Test
  fun `Given expected exception with quickfixes When customize notificationData Then message was completely overridden`() {
    val originalTitle = "Test error title"
    val messageErrorText = "Test error message"
    val expectedMessage = """
      |${invalidGradleJdkErrorText}
      |${messageErrorText}
      |<a href="${baseId()}.embedded">Use Embedded JDK ($embeddedJdkPath)</a>
      |<a href="${OpenProjectJdkLocationListener.ID}">Change Gradle JDK location</a>
    """.trimMargin()

    val notificationMessage = """
      |${invalidGradleJdkErrorText}
      |<a href="any.id">Test quick fix</a>
      |<a href="any.id.2">Test quick fix 2</a>
    """.trimMargin()
    val notificationData = NotificationData(originalTitle, notificationMessage, ERROR, PROJECT_SYNC)
    assertThat(notificationData.registeredListenerIds).isEmpty()
    mockGradleJdkException(messageErrorText)
    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, externalProjectPath, null)

    assertEquals(originalTitle, notificationData.title)
    assertEquals(expectedMessage, notificationData.message)
    assertThat(notificationData.registeredListenerIds).contains("${baseId()}.embedded")
    assertThat(notificationData.registeredListenerIds).contains(OpenProjectJdkLocationListener.ID)
  }

  @Test
  fun `Given expected exception and IdeSdks JDK as embedded When customize notificationData Then message was changed and embedded JDK was suggested`() {
    val originalTitle = "Test error title"
    val messageErrorText = "Test error message"
    val expectedMessage = """
      |${invalidGradleJdkErrorText}
      |${messageErrorText}
      |<a href="${baseId()}.embedded">Use Embedded JDK ($embeddedJdkPath)</a>
      |<a href="${OpenProjectJdkLocationListener.ID}">Change Gradle JDK location</a>
    """.trimMargin()

    val notificationData = NotificationData(originalTitle, invalidGradleJdkErrorText, ERROR, PROJECT_SYNC)
    assertThat(notificationData.registeredListenerIds).isEmpty()
    mockGradleJdkException(messageErrorText)
    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, externalProjectPath, null)

    assertEquals(originalTitle, notificationData.title)
    assertEquals(expectedMessage, notificationData.message)
    assertThat(notificationData.registeredListenerIds).contains("${baseId()}.embedded")
    assertThat(notificationData.registeredListenerIds).contains(OpenProjectJdkLocationListener.ID)
  }

  @Test
  fun `Given expected exception and IdeSdks JDK different to embedded When customize notificationData Then this JDK was suggested`() {
    overrideJdkTo8()

    val originalTitle = "Test error title"
    val messageErrorText = "Test error message"
    val expectedMessage = """
      |${invalidGradleJdkErrorText}
      |${messageErrorText}
      |<a href="${baseId()}.embedded">Use Embedded JDK ($embeddedJdkPath)</a>
      |<a href="${baseId()}">Use JDK ${IdeSdks.getInstance().jdk?.name} (${IdeSdks.getInstance().jdk?.homePath})</a>
      |<a href="${OpenProjectJdkLocationListener.ID}">Change Gradle JDK location</a>
    """.trimMargin()

    mockGradleJdkException(messageErrorText)
    val notificationData = NotificationData(originalTitle, invalidGradleJdkErrorText, ERROR, PROJECT_SYNC)
    assertThat(notificationData.registeredListenerIds).isEmpty()
    gradleJvmExtension.customize(notificationData, gradleProjectRule.project, externalProjectPath, null)

    assertEquals(originalTitle, notificationData.title)
    assertEquals(expectedMessage, notificationData.message)
    assertThat(notificationData.registeredListenerIds).contains("${baseId()}.embedded")
    assertThat(notificationData.registeredListenerIds).contains(baseId())
    assertThat(notificationData.registeredListenerIds).contains(OpenProjectJdkLocationListener.ID)
  }

  private fun mockGradleJdkException(message: String) {
    val gradleJdkException = mock<GradleJdkException>()
    whenever(gradleJdkException.message).thenReturn(message)
    val gradleJdkValidationManager = mock<GradleJdkValidationManager>()
    whenever(gradleJdkValidationManager.validateProjectGradleJvmPath(any(), anyString())).thenReturn(gradleJdkException)
    gradleProjectRule.project.replaceService(GradleJdkValidationManager::class.java, gradleJdkValidationManager, gradleProjectRule.fixture.projectDisposable)
  }
}