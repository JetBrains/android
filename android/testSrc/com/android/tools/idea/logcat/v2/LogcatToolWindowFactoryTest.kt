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
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import java.io.File

/** Tests for [LogcatToolWindowFactory] */
@Suppress("SameParameterValue")
@RunsInEdt
class LogcatToolWindowFactoryTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val mockAdbService = mock<AdbService>()

  private val notifications = mutableListOf<NotificationInfo>()
  private lateinit var fakeAdb: File
  private val toolWindow by lazy {
    object : ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project) {
      override fun getId(): String = "Logcat V2"
    }
  }
  private val logcatToolWindowFactory: LogcatToolWindowFactory = LogcatToolWindowFactory()

  @Before
  fun setUp() {
    ApplicationManager.getApplication()
      .registerServiceInstance(AdbService::class.java, mockAdbService, projectRule.testRootDisposable)

    fakeAdb = File.createTempFile("fakeAdb", "")
    System.setProperty(AndroidSdkUtils.ADB_PATH_PROPERTY, fakeAdb.path)

    projectRule.project.messageBus.connect(projectRule.testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications.add(NotificationInfo(notification.title, notification.content, notification.type))
      }
    })
  }

  @After
  fun tearDown() {
    fakeAdb.delete()
    notifications.clear()
  }

  @Test
  fun createToolWindowContent() {
    val device = mockDevice("device", "serial")
    setupAdbService(arrayOf(device))

    logcatToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.contentManager.contents.map { it.displayName }).containsExactly(device.name)
  }

  @Test
  fun createToolWindowContent_noAdb() {
    System.setProperty(AndroidSdkUtils.ADB_PATH_PROPERTY, "no-adb")

    logcatToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.contentManager.contents).isEmpty()
    assertThat(notifications).containsExactly(NotificationInfo(title = "", content = "ADB binary not found", NotificationType.WARNING))
  }

  @Test
  fun createToolWindowContent_getDebugBridgeThrows() {
    setupAdbService(RuntimeException("exception"))

    logcatToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(toolWindow.contentManager.contents).isEmpty()
    assertThat(notifications).containsExactly(NotificationInfo(
      title = "ADB connection error",
      content = "java.lang.RuntimeException: exception",
      NotificationType.WARNING))
  }

  private fun setupAdbService(devices: Array<IDevice>) {
    val mockAndroidDebugBridge = mock<AndroidDebugBridge>()
    `when`(mockAndroidDebugBridge.devices).thenReturn(devices)
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(Futures.immediateFuture(mockAndroidDebugBridge))
  }

  private fun setupAdbService(throwable: Throwable) {
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(Futures.immediateFailedFuture(throwable))
  }

  private fun mockDevice(name: String, serialNumber: String): IDevice {
    val device = mock<IDevice>()
    `when`(device.name).thenReturn(name)
    `when`(device.serialNumber).thenReturn(serialNumber)
    return device
  }

  private data class NotificationInfo(val title: String, val content: String, val type: NotificationType)
}