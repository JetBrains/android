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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class LiveEditDevicesTestInfo {

  val device1: IDevice = MockitoKt.mock()
  val device2: IDevice = MockitoKt.mock()

  @Test
  fun testDeviceAdd() {
    val map = LiveEditDevices()

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    assertEquals(LiveEditStatus.UpToDate, map.getInfo(device1)!!.status)
    assertEquals(LiveEditStatus.UnrecoverableError, map.getInfo(device2)!!.status)

    // Listener should fire on device adds.
    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.UpToDate)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.UnrecoverableError)
  }

  @Test
  fun testUpdateAll() {
    val map = LiveEditDevices()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update(LiveEditStatus.Disabled)

    assertEquals(LiveEditStatus.Disabled, map.getInfo(device1)!!.status)
    assertEquals(LiveEditStatus.Disabled, map.getInfo(device2)!!.status)

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.Disabled)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }

  @Test
  fun testUpdateAllWithFunction() {
    val map = LiveEditDevices()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update { _, it -> if (it.unrecoverable()) LiveEditStatus.Disabled else it }

    assertEquals(LiveEditStatus.UpToDate, map.getInfo(device1)!!.status)
    assertEquals(LiveEditStatus.Disabled, map.getInfo(device2)!!.status)

    assertEquals(1, events.size)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }

  @Test
  fun testUpdateOne() {
    val map = LiveEditDevices()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update(device1, LiveEditStatus.UnrecoverableError)
    map.update(device2, LiveEditStatus.Disabled)

    assertEquals(LiveEditStatus.UnrecoverableError, map.getInfo(device1)!!.status)
    assertEquals(LiveEditStatus.Disabled, map.getInfo(device2)!!.status)

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.UnrecoverableError)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }

  @Test
  fun testUpdateOneWithFunction() {
    val map = LiveEditDevices()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update(device1) { _, it -> if (it == LiveEditStatus.UpToDate) LiveEditStatus.Disabled else it }
    map.update(device2) { _, it -> if (it.unrecoverable()) LiveEditStatus.Disabled else it }

    assertEquals(LiveEditStatus.Disabled, map.getInfo(device1)!!.status)
    assertEquals(LiveEditStatus.Disabled, map.getInfo(device2)!!.status)

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.Disabled)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }

  @Test
  fun testDisabled() {
    val map = LiveEditDevices()
    assertTrue(map.isDisabled())

    map.addDevice(device1, LiveEditStatus.UpToDate)
    assertFalse(map.isDisabled())

    map.update(device1) { _, it -> if (it == LiveEditStatus.UpToDate) LiveEditStatus.Disabled else it }
    assertTrue(map.isDisabled())

    map.update(device1) { _, it -> if (it == LiveEditStatus.Disabled) LiveEditStatus.UpToDate else it }
    assertFalse(map.isDisabled())

    map.addDevice(device2, LiveEditStatus.UnrecoverableError)
    assertFalse(map.isDisabled())

    map.update(device1) { _, it -> if (it == LiveEditStatus.UpToDate) LiveEditStatus.Disabled else it }
    assertFalse(map.isDisabled())

    map.update(device2) { _, it -> if (it.unrecoverable()) LiveEditStatus.Disabled else it }
    assertTrue(map.isDisabled())
  }

}
