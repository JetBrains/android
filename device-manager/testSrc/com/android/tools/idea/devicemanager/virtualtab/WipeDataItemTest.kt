/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab

import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.devicemanager.CountDownLatchAssert
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback
import com.android.tools.idea.devicemanager.Key
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures.immediateFuture
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.awt.Component
import java.util.concurrent.CountDownLatch

@RunWith(JUnit4::class)
class WipeDataItemTest {

  private val myAvd: AvdInfo
  private val myConnection: AvdManagerConnection
  private val myTable: VirtualDeviceTable
  private val myEditor: VirtualDevicePopUpMenuButtonTableCellEditor

  init {
    myAvd = mock()
    myConnection = mock()
    myTable = mock()
    val panel = mock<VirtualDevicePanel>()
    whenever(panel.table).thenReturn(myTable)
    myEditor = mock()
    whenever(myEditor.panel).thenReturn(panel)
  }

  @Test
  fun wipeDataItemDeviceIsOnline() {
    // Arrange
    whenever(myEditor.device).thenReturn(TestVirtualDevices.onlinePixel5Api31(myAvd))

    val item = WipeDataItem(myEditor,
                            { component -> showCannotWipeARunningAvdDialog(component) },
                            { device, component -> false },
                            { myConnection },
                            { table -> WipeDataItem.newSetSelectedDeviceFutureCallback(table) })

    // Act
    item.doClick()

    // Assert
    verify(myTable, never()).setSelectedDevice(TestVirtualDevices.PIXEL_5_API_31_KEY)
  }

  @Test
  fun wipeDataItemNotWipe() {
    // Arrange
    whenever(myEditor.device).thenReturn(TestVirtualDevices.pixel5Api31(myAvd))

    val item = WipeDataItem(myEditor,
                            { component -> showCannotWipeARunningAvdDialog(component) },
                            { device, component -> false },
                            { myConnection },
                            { table -> WipeDataItem.newSetSelectedDeviceFutureCallback(table) })

    // Act
    item.doClick()

    // Assert
    verify(myTable, never()).setSelectedDevice(TestVirtualDevices.PIXEL_5_API_31_KEY)
  }

  @Test
  fun wipeDataItem() {
    // Arrange
    whenever(myEditor.device).thenReturn(TestVirtualDevices.pixel5Api31(myAvd))
    whenever(myConnection.wipeUserDataAsync(myAvd)).thenReturn(immediateFuture(true))
    whenever(myTable.reloadDevice(TestVirtualDevices.PIXEL_5_API_31_KEY)).thenReturn(immediateFuture(TestVirtualDevices.PIXEL_5_API_31_KEY))

    val latch = CountDownLatch(1)
    val item = WipeDataItem(myEditor,
                            { component -> showCannotWipeARunningAvdDialog(component) },
                            { device, component -> true },
                            { myConnection },
                            { table -> newSetSelectedDeviceFutureCallback(table, latch) })

    // Act
    item.doClick()

    // Assert
    CountDownLatchAssert.await(latch)
    verify(myTable).setSelectedDevice(TestVirtualDevices.PIXEL_5_API_31_KEY)
  }

  companion object {
    private fun newSetSelectedDeviceFutureCallback(table: VirtualDeviceTable, latch: CountDownLatch): FutureCallback<Key> =
      CountDownLatchFutureCallback(WipeDataItem.newSetSelectedDeviceFutureCallback(table), latch)

    private fun showCannotWipeARunningAvdDialog(component: Component) {}
  }
}
