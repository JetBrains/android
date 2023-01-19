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

@RunWith(JUnit4::class)
class DeviceStatusManagerTest {

  val device1: IDevice = MockitoKt.mock()
  val device2: IDevice = MockitoKt.mock()

  @Test
  fun testDeviceAdd() {
    val map = DeviceStatusManager()

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    assertEquals(LiveEditStatus.UpToDate, map.get(device1))
    assertEquals(LiveEditStatus.UnrecoverableError, map.get(device2))

    // Listener should fire on device adds.
    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.UpToDate)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.UnrecoverableError)
  }

  @Test
  fun testUpdateAll() {
    val map = DeviceStatusManager()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update(LiveEditStatus.Disabled)

    assertEquals(LiveEditStatus.Disabled, map.get(device1))
    assertEquals(LiveEditStatus.Disabled, map.get(device2))

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.Disabled)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }

  @Test
  fun testUpdateAllWithFunction() {
    val map = DeviceStatusManager()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update { if (it.unrecoverable()) LiveEditStatus.Disabled else it }

    assertEquals(LiveEditStatus.UpToDate, map.get(device1))
    assertEquals(LiveEditStatus.Disabled, map.get(device2))

    assertEquals(1, events.size)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }

  @Test
  fun testUpdateOne() {
    val map = DeviceStatusManager()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update(device1, LiveEditStatus.UnrecoverableError)
    map.update(device2, LiveEditStatus.Disabled)

    assertEquals(LiveEditStatus.UnrecoverableError, map.get(device1))
    assertEquals(LiveEditStatus.Disabled, map.get(device2))

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.UnrecoverableError)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }

  @Test
  fun testUpdateOneWithFunction() {
    val map = DeviceStatusManager()
    map.addDevice(device1, LiveEditStatus.UpToDate)
    map.addDevice(device2, LiveEditStatus.UnrecoverableError)

    val events = mutableMapOf<IDevice, LiveEditStatus>()
    map.addListener { events.putAll(it) }

    map.update(device1) { if (it == LiveEditStatus.UpToDate) LiveEditStatus.Disabled else it }
    map.update(device2) { if (it.unrecoverable()) LiveEditStatus.Disabled else it }

    assertEquals(LiveEditStatus.Disabled, map.get(device1))
    assertEquals(LiveEditStatus.Disabled, map.get(device2))

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], LiveEditStatus.Disabled)
    assertContains(events, device2)
    assertEquals(events[device2], LiveEditStatus.Disabled)
  }



}
