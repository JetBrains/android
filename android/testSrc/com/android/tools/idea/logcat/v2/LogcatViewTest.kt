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
package com.android.tools.idea.logcat.v2

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`

/** Tests for [LogcatView] */
@RunsInEdt
class LogcatViewTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val mockBridge = mock<AndroidDebugBridge>()
  private val toolWindow by lazy { ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project) }

  private val device1 = mockDevice("device1", "serial1")
  private val device2 = mockDevice("device2", "serial2")

  @Test
  fun construct_addsContents() {
    `when`(mockBridge.devices).thenReturn(arrayOf(device1, device2))

    LogcatView(projectRule.project, toolWindow, mockBridge)

    assertThat(toolWindow.contentManager.contents.map { it.displayName }).containsExactly(device1.name, device2.name).inOrder()
    assertThat(toolWindow.contentManager.contents[0].isCloseable).isFalse()
    assertThat(toolWindow.contentManager.contents[1].isCloseable).isFalse()
  }

  @Test
  fun deviceConnected_addsContent() {
    `when`(mockBridge.devices).thenReturn(emptyArray())
    LogcatView(projectRule.project, toolWindow, mockBridge)

    AndroidDebugBridge.deviceConnected(device1)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.contentManager.contents.map { it.displayName }).containsExactly(device1.name)
    assertThat(toolWindow.contentManager.contents[0].isCloseable).isFalse()
  }

  @Test
  fun deviceDisconnected_makesClosable() {
    `when`(mockBridge.devices).thenReturn(arrayOf(device1))
    LogcatView(projectRule.project, toolWindow, mockBridge)

    AndroidDebugBridge.deviceDisconnected(device1)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.contentManager.contents.map { it.displayName }).containsExactly(device1.name)
    assertThat(toolWindow.contentManager.contents[0].isCloseable).isTrue()
  }

  @Test
  fun deviceReconnected_makesUnclosable() {
    `when`(mockBridge.devices).thenReturn(arrayOf(device1))
    LogcatView(projectRule.project, toolWindow, mockBridge)
    val content = toolWindow.contentManager.contents[0]
    AndroidDebugBridge.deviceDisconnected(device1)
    AndroidDebugBridge.deviceConnected(device1)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.contentManager.contents.map { it.displayName }).containsExactly(device1.name)
    assertThat(toolWindow.contentManager.contents[0]).isSameAs(content)
    assertThat(toolWindow.contentManager.contents[0].isCloseable).isFalse()
  }

  private fun mockDevice(name: String, serialNumber: String): IDevice {
    val device = mock<IDevice>()
    `when`(device.name).thenReturn(name)
    `when`(device.serialNumber).thenReturn(serialNumber)
    return device
  }
}