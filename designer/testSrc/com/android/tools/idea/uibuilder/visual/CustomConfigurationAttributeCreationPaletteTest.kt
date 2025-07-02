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
package com.android.tools.idea.uibuilder.visual

import com.android.sdklib.devices.Device
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.type.LayoutFileType
import java.awt.event.ActionEvent
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JPanel
import org.mockito.Mockito

class CustomConfigurationAttributeCreationPaletteTest : LayoutTestCase() {
  override fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testCreateConfiguration() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    // Temp class for Mockito to verify callback.
    open class MyConsumer : Consumer<String> {
      override fun accept(t: String) = Unit
    }
    val mockedConsumer = Mockito.mock(MyConsumer::class.java)

    val palette =
      CustomConfigurationAttributeCreationPalette(file.virtualFile, myModule) {
        mockedConsumer.accept(it.name)
      }

    val addButton =
      (palette.components[2] as JPanel).components.filterIsInstance<JButton>().first {
        it.text == "Add"
      }
    addButton.action.actionPerformed(Mockito.mock(ActionEvent::class.java))
    Mockito.verify(mockedConsumer).accept("Preview")
  }

  fun testDeviceGrouping() {
    val nexusDevice = Mockito.mock(Device::class.java)
    val wearDevice = Mockito.mock(Device::class.java)
    val genericDevice1 = Mockito.mock(Device::class.java)
    val genericDevice2 = Mockito.mock(Device::class.java)
    val xrDevice = Mockito.mock(Device::class.java)
    val otherDevice = Mockito.mock(Device::class.java)

    // Create a map with an arbitrary order to test the sorting.
    val groupedDevices =
      mapOf(
        DeviceGroup.GENERIC to listOf(genericDevice1, genericDevice2),
        DeviceGroup.NEXUS to listOf(nexusDevice),
        DeviceGroup.OTHER to listOf(otherDevice),
        DeviceGroup.WEAR to listOf(wearDevice),
        DeviceGroup.XR to listOf(xrDevice),
      )

    val sortedMap = getDeviceGroupsSortedAsMap(groupedDevices)

    // 1. Verify that all devices are present in the output map.
    val allOriginalDevices = groupedDevices.values.flatten()
    val allSortedDevices = sortedMap.values.flatten()
    assertEquals(allOriginalDevices.size, allSortedDevices.size)
    // Using sets ensures that we check for content equality, regardless of order.
    assertEquals(allOriginalDevices.toSet(), allSortedDevices.toSet())

    // 2. Verify that the devices are still correctly mapped to their original groups.
    assertEquals(listOf(genericDevice1, genericDevice2), sortedMap[DeviceGroup.GENERIC])
    assertEquals(listOf(nexusDevice), sortedMap[DeviceGroup.NEXUS])
    assertEquals(listOf(wearDevice), sortedMap[DeviceGroup.WEAR])
    assertEquals(listOf(xrDevice), sortedMap[DeviceGroup.XR])
    assertEquals(listOf(otherDevice), sortedMap[DeviceGroup.OTHER])

    // 3. Verify that the map keys (DeviceGroup) are sorted according to the predefined order.
    val expectedKeyOrder =
      listOf(
        DeviceGroup.WEAR,
        DeviceGroup.XR,
        DeviceGroup.GENERIC,
        DeviceGroup.NEXUS,
        DeviceGroup.OTHER,
      )
    val actualKeyOrder = sortedMap.keys.toList()
    assertEquals(expectedKeyOrder, actualKeyOrder)
  }
}

private const val LAYOUT_FILE_CONTENT =
  """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""
