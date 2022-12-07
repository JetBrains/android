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
import com.android.tools.idea.editors.literals.EditState
import com.android.tools.idea.editors.literals.EditStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertContains
import kotlin.test.assertEquals

@RunWith(JUnit4::class)
class DeviceStatusManagerTest {

  private val upToDate = EditStatus(EditState.UP_TO_DATE, "", "")
  private val error = EditStatus(EditState.ERROR, "", "")
  private val disabled = EditStatus(EditState.DISABLED, "", "")

  val device1: IDevice = MockitoKt.mock()
  val device2: IDevice = MockitoKt.mock()

  @Test
  fun testDeviceAdd() {
    val map = DeviceStatusManager()

    val events = mutableMapOf<IDevice, EditStatus>()
    map.addListener { events.putAll(it) }

    map.addDevice(device1, upToDate)
    map.addDevice(device2, error)

    assertEquals(upToDate, map.get(device1))
    assertEquals(error, map.get(device2))

    // Listener should fire on device adds.
    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], upToDate)
    assertContains(events, device2)
    assertEquals(events[device2], error)
  }

  @Test
  fun testUpdateAll() {
    val map = DeviceStatusManager()
    map.addDevice(device1, upToDate)
    map.addDevice(device2, error)

    val events = mutableMapOf<IDevice, EditStatus>()
    map.addListener { events.putAll(it) }

    map.update(disabled)

    assertEquals(disabled, map.get(device1))
    assertEquals(disabled, map.get(device2))

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], disabled)
    assertContains(events, device2)
    assertEquals(events[device2], disabled)
  }

  @Test
  fun testUpdateAllWithFunction() {
    val map = DeviceStatusManager()
    map.addDevice(device1, upToDate)
    map.addDevice(device2, error)

    val events = mutableMapOf<IDevice, EditStatus>()
    map.addListener { events.putAll(it) }

    map.update { if (it.editState == EditState.ERROR) disabled else it }

    assertEquals(upToDate, map.get(device1))
    assertEquals(disabled, map.get(device2))

    assertEquals(1, events.size)
    assertContains(events, device2)
    assertEquals(events[device2], disabled)
  }

  @Test
  fun testUpdateOne() {
    val map = DeviceStatusManager()
    map.addDevice(device1, upToDate)
    map.addDevice(device2, error)

    val events = mutableMapOf<IDevice, EditStatus>()
    map.addListener { events.putAll(it) }

    map.update(device1, error)
    map.update(device2, disabled)

    assertEquals(error, map.get(device1))
    assertEquals(disabled, map.get(device2))

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], error)
    assertContains(events, device2)
    assertEquals(events[device2], disabled)
  }

  @Test
  fun testUpdateOneWithFunction() {
    val map = DeviceStatusManager()
    map.addDevice(device1, upToDate)
    map.addDevice(device2, error)

    val events = mutableMapOf<IDevice, EditStatus>()
    map.addListener { events.putAll(it) }

    map.update(device1) { if (it.editState == EditState.UP_TO_DATE) disabled else it }
    map.update(device2) { if (it.editState == EditState.ERROR) disabled else it }

    assertEquals(disabled, map.get(device1))
    assertEquals(disabled, map.get(device2))

    assertEquals(2, events.size)
    assertContains(events, device1)
    assertEquals(events[device1], disabled)
    assertContains(events, device2)
    assertEquals(events[device2], disabled)
  }



}
