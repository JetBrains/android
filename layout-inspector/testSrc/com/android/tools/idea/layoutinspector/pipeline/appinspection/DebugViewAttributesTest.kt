/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.project.AndroidNotification
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.NotificationType
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class DebugViewAttributesTest {
  @get:Rule val projectRule = ProjectRule()
  private val process = MODERN_DEVICE.createProcess()
  private val deviceSelector = DeviceSelector.fromSerialNumber(process.device.serial)
  private val device = process.device

  private var adbSession = FakeAdbSession()

  private lateinit var mockAndroidNotificationService: AndroidNotification

  @Before
  fun setUp() {
    mockAndroidNotificationService = mock()
    projectRule.project.replaceService(
      AndroidNotification::class.java,
      mockAndroidNotificationService,
      projectRule.project,
    )
  }

  @Test
  fun testEnableSettingSuccess() = runBlocking {
    adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings get global debug_view_attributes",
      "0",
    )
    adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings put global debug_view_attributes 1",
      "",
    )

    assertThat(DebugViewAttributes(projectRule.project, adbSession).set(device)).isTrue()
    assertThat(adbSession.deviceServices.shellV2Requests.size).isEqualTo(2)
    assertThat(adbSession.deviceServices.shellV2Requests.poll().command)
      .isEqualTo("settings get global debug_view_attributes")
    assertThat(adbSession.deviceServices.shellV2Requests.poll().command)
      .isEqualTo("settings put global debug_view_attributes 1")
  }

  @Test
  fun testEnableSettingFailure() = runBlocking {
    adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings get global debug_view_attributes",
      "0",
    )
    adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings put global debug_view_attributes 1",
      "",
      "error",
    )

    assertThat(DebugViewAttributes(projectRule.project, adbSession).set(device)).isFalse()
    assertThat(adbSession.deviceServices.shellV2Requests.size).isEqualTo(2)
    assertThat(adbSession.deviceServices.shellV2Requests.poll().command)
      .isEqualTo("settings get global debug_view_attributes")
    assertThat(adbSession.deviceServices.shellV2Requests.poll().command)
      .isEqualTo("settings put global debug_view_attributes 1")

    // Assert that the balloon is shown.
    verify(mockAndroidNotificationService)
      .showBalloon(eq("Could not enable resolution traces"), any(), eq(NotificationType.WARNING))
  }

  @Test
  fun testSettingIsNotEnabledIfAlreadyEnabled() = runBlocking {
    adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings get global debug_view_attributes",
      "1",
    )
    adbSession.deviceServices.configureShellCommand(
      deviceSelector,
      "settings put global debug_view_attributes 1",
      "",
    )

    assertThat(DebugViewAttributes(projectRule.project, adbSession).set(device)).isFalse()
    assertThat(adbSession.deviceServices.shellV2Requests.size).isEqualTo(1)
    assertThat(adbSession.deviceServices.shellV2Requests.poll().command)
      .isEqualTo("settings get global debug_view_attributes")
  }
}
