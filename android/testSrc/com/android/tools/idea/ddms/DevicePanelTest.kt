// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.ddms

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JComboBox
import javax.swing.plaf.basic.BasicComboBoxRenderer

internal class DevicePanelTest {
  private lateinit var myProject: Project
  private lateinit var myBridge: AndroidDebugBridge

  private lateinit var myPanel: DevicePanel
  private lateinit var myDeviceComboBox: JComboBox<IDevice>

  @Before
  fun setUp() {
    myProject = Mockito.mock(Project::class.java)
    myBridge = Mockito.mock(AndroidDebugBridge::class.java)

    myPanel = DevicePanel(myProject, Mockito.mock(DeviceContext::class.java), false)

    myDeviceComboBox = myPanel.deviceComboBox
    myDeviceComboBox.renderer = BasicComboBoxRenderer.UIResource()
  }

  @After
  fun disposeOfProject() {
    Disposer.dispose(myProject)
  }

  @Test
  fun updateDeviceCombo() {
    val device = mockDevice()
    Mockito.`when`(myBridge.devices).thenReturn(arrayOf(device))

    myPanel.setBridge(myBridge)
    myPanel.setIgnoringActionEvents(true)
    myPanel.putPreferredClient("emulator-5554", "com.google.myapplication")
    myDeviceComboBox.addItem(mockDevice())

    myPanel.updateDeviceCombo()

    assertEquals(1, myDeviceComboBox.itemCount)
    assertSame(device, myDeviceComboBox.selectedItem)
  }

  @Test
  fun updateDeviceComboNullSelectedDeviceDoesntGetAdded() {
    Mockito.`when`(myBridge.devices).thenReturn(emptyArray())

    myPanel.setBridge(myBridge)
    myPanel.updateDeviceCombo()

    val device = mockDevice()
    Mockito.`when`(myBridge.devices).thenReturn(arrayOf(device))

    myPanel.putPreferredClient("emulator-5554", "com.google.myapplication")
    myPanel.updateDeviceCombo()

    assertEquals(1, myDeviceComboBox.itemCount)
    assertSame(device, myDeviceComboBox.selectedItem)
  }

  private fun mockDevice(): IDevice {
    val device = Mockito.mock(IDevice::class.java)

    Mockito.`when`(device.clients).thenReturn(emptyArray())
    Mockito.`when`(device.isEmulator).thenReturn(true)
    Mockito.`when`(device.name).thenReturn("emulator-5554")

    return device
  }
}
