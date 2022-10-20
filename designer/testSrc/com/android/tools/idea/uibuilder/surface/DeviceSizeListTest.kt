/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import org.jetbrains.android.AndroidTestBase

class DeviceSizeListTest: AndroidTestBase() {

  private val NEXUS_5 = buildDeviceSize("Nexus 5", 900, 1760)
  private val NEXUS_6P = buildDeviceSize("Nexus 6p", 1131, 2011)
  private val NEXUS_7 = buildDeviceSize("Nexus 7", 1652, 2644)
  private val NEXUS_9 = buildDeviceSize("Nexus 9", 2816, 2112)
  private val NEXUS_10 = buildDeviceSize("Nexus 10", 3520, 2200)

  fun testSort() {
    val list = buildDefaultSizeList()

    assertEquals(list.myList[0], NEXUS_5)
    assertEquals(list.myList[1], NEXUS_6P)
    assertEquals(list.myList[2], NEXUS_7)
    assertEquals(list.myList[3], NEXUS_9)
    assertEquals(list.myList[4], NEXUS_10)
  }

  fun testSnap() {
    val list = buildDefaultSizeList()

    verifySnap(list, NEXUS_5, NEXUS_5.x + 10, NEXUS_5.y - 20, 34)
    verifySnap(list, NEXUS_6P, NEXUS_6P.x - 21, NEXUS_6P.y + 12, 100)
    verifySnap(list, NEXUS_7, NEXUS_7.x + 35, NEXUS_7.y - 19, 54)
    verifySnap(list, NEXUS_9, NEXUS_9.x - 7, NEXUS_9.y + 34, 40)
    verifySnap(list, NEXUS_10, NEXUS_10.x + 121, NEXUS_10.y - 102, 224)
  }

  fun testNoSnap() {
    val list = buildDefaultSizeList()

    assertNull(list.snapToDevice(NEXUS_5.x + 100, NEXUS_5.y - 20, 3))
    assertNull(list.snapToDevice(NEXUS_6P.x - 21, NEXUS_6P.y + 12, 1))
    assertNull(list.snapToDevice(NEXUS_7.x + 35, NEXUS_7.y - 19, 5))
    assertNull(list.snapToDevice(NEXUS_9.x - 7, NEXUS_9.y + 34, 4))
    assertNull(list.snapToDevice(NEXUS_10.x + 121, NEXUS_10.y - 102, 2))
  }

  private fun verifySnap(list: DeviceSizeList, device: DeviceSizeList.DeviceSize, x: Int, y: Int, snap: Int) {
    val size = list.snapToDevice(x, y, snap)
    assertEquals(device.x, size!!.x)
    assertEquals(device.y, size.y)

    val reversePoint = list.snapToDevice(y, x, snap)
    assertEquals(device.x, reversePoint!!.y)
    assertEquals(device.y, reversePoint.x)
  }

  private fun buildDeviceSize(name: String, x: Int, y: Int): DeviceSizeList.DeviceSize {
    return DeviceSizeList.DeviceSize.create(buildDevice(name), x, y)
  }

  private fun buildDefaultSizeList() : DeviceSizeList {
    val list = DeviceSizeList()
    list.myList.add(NEXUS_6P)
    list.myList.add(NEXUS_10)
    list.myList.add(NEXUS_5)
    list.myList.add(NEXUS_9)
    list.myList.add(NEXUS_7)
    list.sort()
    return list
  }

  private fun buildDevice(name: String): Device {
    val builder = Device.Builder()
    builder.setName(name)
    builder.setManufacturer(name)
    builder.addSoftware(Software())
    val state = State()
    state.isDefaultState = true
    state.hardware = Hardware()
    builder.addState(state)
    return builder.build()
  }
}
