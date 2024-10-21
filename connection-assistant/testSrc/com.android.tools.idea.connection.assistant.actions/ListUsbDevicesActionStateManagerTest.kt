/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions

import com.android.ddmlib.AdbDevice
import com.android.ddmlib.IDevice
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.datamodel.DefaultActionState
import com.android.tools.usb.UsbDevice
import com.android.tools.usb.UsbDeviceCollector
import com.intellij.ide.IdeEventQueue
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.CompletableFuture

class ListUsbDevicesActionStateManagerTest : AndroidTestCase() {
  private val testUsbDeviceCollector: UsbDeviceCollector = mock {
    on { listUsbDevices() } doReturn CompletableFuture.completedFuture(listOf())
  }
  private val emptyActionData: ActionData = mock()
  private lateinit var myStateManager: ListUsbDevicesActionStateManager

  private lateinit var rawDevices: CompletableFuture<List<AdbDevice>>
  private lateinit var devices: List<IDevice>

  override fun setUp() {
    super.setUp()
    myStateManager = ListUsbDevicesActionStateManager()
    rawDevices = CompletableFuture.completedFuture(emptyList())
    devices = emptyList()
    myStateManager.init(project, emptyActionData, testUsbDeviceCollector, { rawDevices }, { devices } )
  }

  @Test
  fun testDefaultState() {
    myStateManager.refresh()
    IdeEventQueue.getInstance().flushQueue();
    TestCase.assertEquals(DefaultActionState.ERROR_RETRY, myStateManager.getState(project, emptyActionData))
  }

  @Test
  fun testLoadingState() {
    whenever(testUsbDeviceCollector.listUsbDevices()).thenReturn(CompletableFuture())
    myStateManager.refresh()
    IdeEventQueue.getInstance().flushQueue();
    TestCase.assertEquals(DefaultActionState.IN_PROGRESS, myStateManager.getState(project, emptyActionData))
  }

  @Test
  fun testSingleDevice() {
    val devices = ArrayList<UsbDevice>()
    devices.add(UsbDevice("test", "test", "test"))
    whenever(testUsbDeviceCollector.listUsbDevices()).thenReturn(CompletableFuture.completedFuture(devices))
    myStateManager.refresh()
    IdeEventQueue.getInstance().flushQueue();
    TestCase.assertEquals(CustomSuccessState, myStateManager.getState(project, emptyActionData))
  }

  @Test
  fun testException() {
    val exceptionFuture = CompletableFuture<List<UsbDevice>>()
    exceptionFuture.completeExceptionally(IOException())
    whenever(testUsbDeviceCollector.listUsbDevices()).thenReturn(exceptionFuture)
    myStateManager.refresh()
    IdeEventQueue.getInstance().flushQueue();
    TestCase.assertEquals(DefaultActionState.ERROR_RETRY, myStateManager.getState(project, emptyActionData))
  }
}