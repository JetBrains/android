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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.run.LaunchCompatibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Arrays

class SelectMultipleDevicesDialogTableTest {
  private var table: SelectMultipleDevicesDialogTable = SelectMultipleDevicesDialogTable()

  @Test
  fun getSelectedTargets() {
    val device = createDevice("Pixel 8")
    table.setModel(SelectMultipleDevicesDialogTableModel(listOf(device)))

    table.setSelected(true, 0)

    assertThat(table.selectedTargets).containsExactly(DeploymentTarget(device, DefaultBoot))
  }

  @Test
  fun setSelectedTargets() {
    val device = createDevice("Pixel 8")
    table.setModel(SelectMultipleDevicesDialogTableModel(listOf(device)))

    table.selectedTargets = listOf(DeploymentTarget(device, DefaultBoot))

    assertThat(table.isSelected(0)).isTrue()
  }

  @Test
  fun emptyModel() {
    val model = SelectMultipleDevicesDialogTableModel(emptyList())

    table.setModel(model)
  }

  @Test
  fun setModel() {
    val device = createDevice("Pixel 8")
    val model = SelectMultipleDevicesDialogTableModel(listOf(device))

    table.setModel(model)

    assertThat(table.data)
      .isEqualTo(listOf(listOf("", "Type", "Device"), listOf(false, device.icon, "Pixel 8")))
  }

  @Test
  fun deviceWithError() {
    val device =
      createDevice(
        "Pixel 5",
        launchCompatibility =
          LaunchCompatibility(LaunchCompatibility.State.ERROR, "Missing system image"),
      )
    val model = SelectMultipleDevicesDialogTableModel(listOf(device))

    table.setModel(model)

    val data =
      listOf(
        listOf("", "Type", "Device"),
        listOf(
          false,
          device.icon,
          "<html>Pixel 5<br><font size=-2 color=#999999>Missing system image</font></html>",
        ),
      )
    assertThat(table.data).isEqualTo(data)
  }

  @Test
  fun ambiguousNamedDevices() {
    val device1 = createDevice("Pixel 5", disambiguator = "ABC123")
    val device2 = createDevice("Pixel 5", disambiguator = "DEF456")

    val model = SelectMultipleDevicesDialogTableModel(Arrays.asList(device1, device2))

    // Act
    table.setModel(model)

    val data =
      listOf(
        listOf("", "Type", "Device", "Serial Number"),
        listOf(false, device1.icon, "Pixel 5", "ABC123"),
        listOf(false, device2.icon, "Pixel 5", "DEF456"),
      )
    assertThat(table.data).isEqualTo(data)
  }

  @Test
  fun deviceWithSnapshots() {
    val device = createDevice("Pixel 6", hasSnapshots = true)
    val model = SelectMultipleDevicesDialogTableModel(listOf(device))

    table.setModel(model)

    val icon = device.icon

    val data =
      listOf(
        listOf("", "Type", "Device", "Boot Option"),
        listOf(false, icon, "Pixel 6", "Quick Boot"),
        listOf(false, icon, "Pixel 6", "Cold Boot"),
        listOf(false, icon, "Pixel 6", "snap-1"),
      )
    assertThat(table.data).isEqualTo(data)
  }
}
