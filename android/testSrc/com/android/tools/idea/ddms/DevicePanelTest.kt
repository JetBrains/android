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
package com.android.tools.idea.ddms

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.idea.ddms.DevicePanel.DeviceComboBox
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JComboBox

internal class DevicePanelTest {
  private lateinit var myProject: Project
  private lateinit var myBridge: AndroidDebugBridge

  private lateinit var myPanel: DevicePanel
  private lateinit var myDeviceComboBox: JComboBox<IDevice>

  @Before
  fun setUp() {
    myProject = Mockito.mock(Project::class.java)
    myBridge = Mockito.mock(AndroidDebugBridge::class.java)

    myPanel = DevicePanel(myProject, Mockito.mock(DeviceContext::class.java), TestDeviceComboBox(), ComboBox())
    myDeviceComboBox = myPanel.deviceComboBox
  }

  private class TestDeviceComboBox : DeviceComboBox() {
    override fun initRenderer(callback: FutureCallback<DeviceNameProperties>) {
    }

    override fun dispose() {
    }

    override fun setSerialNumbersVisible(visible: Boolean) {
    }
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

  @Test
  fun updateProcessComboBoxDoesntThrowNullPointerExceptionAfterDispose() {
    Disposer.dispose(myPanel)
    myPanel.deviceChangedImpl(mockDevice(), IDevice.CHANGE_CLIENT_LIST)
  }

  @Test
  fun processComboBox_ignoresClientsWithNoName() {
    mockClients(
      mockClient(1, "com.company.app1.package", "com.company.app1.description"),
      mockClient(2, "com.company.app2.package", ""),
      mockClient(3, "com.company.app3.package", null),
    )

    assertEquals(1, myPanel.clientComboBox.itemCount)
    assertEquals("com.company.app1.description", myPanel.clientComboBox.getItemAt(0).clientData.clientDescription)
  }

  // TODO(b/191684793): Add UI tests for myPanel.clientComboBox integration with ComboboxSpeedSearch
  private fun mockClients(vararg clients: Client) {
    val device = mockDevice(clients)
    Mockito.`when`(myBridge.devices).thenReturn(arrayOf(device))
    myPanel.setBridge(myBridge)
    myPanel.putPreferredClient("emulator-5554", "com.google.myapplication")
    myPanel.updateDeviceCombo()
  }

  private fun mockDevice(clients: Array<out Client> = emptyArray()): IDevice {
    val device = Mockito.mock(IDevice::class.java)

    Mockito.`when`(device.clients).thenReturn(clients)
    for (client in clients) {
      Mockito.`when`(client.device).thenReturn(device)
    }
    Mockito.`when`(device.isEmulator).thenReturn(true)
    Mockito.`when`(device.name).thenReturn("emulator-5554")

    return device
  }

  private fun mockClient(pid: Int, packageName: String?, clientDescription: String?): Client {
    val mockData = Mockito.mock(ClientData::class.java)
    Mockito.`when`(mockData.pid).thenReturn(pid)
    Mockito.`when`(mockData.packageName).thenReturn(packageName)
    Mockito.`when`(mockData.clientDescription).thenReturn(clientDescription)
    val mockClient = Mockito.mock(Client::class.java)
    Mockito.`when`(mockClient.clientData).thenReturn(mockData)
    return mockClient
  }
}
