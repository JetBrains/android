/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.Reservation
import com.android.sdklib.deviceprovisioner.ReservationState
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@RunsInEdt
class DeviceNamePanelTest {

  @Test
  fun stateTransitionText() {
    fun rowData(isTransitioning: Boolean, status: String): DeviceRowData {
      val state =
        DeviceState.Disconnected(
          DeviceProperties.buildForTest { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE },
          isTransitioning,
          status,
          null
        )
      val handle = mock<DeviceHandle>()
      whenever(handle.state).thenReturn(state)
      val rowData = mock<DeviceRowData>()
      whenever(rowData.handle).thenReturn(handle)
      return rowData
    }

    assertThat(rowData(true, "Reserving a device").stateTransitionText())
      .isEqualTo("Reserving a device")
    assertThat(rowData(false, "Reserving a device").stateTransitionText()).isNull()
  }

  @Test
  fun reservationText() {
    assertThat(
        Reservation(
            ReservationState.ACTIVE,
            "Connected",
            null,
            Instant.parse("2023-02-03T19:15:30.00Z"),
            null
          )
          .line2Text(ZoneId.of("UTC"))
      )
      .isEqualTo("Connected; device will expire at 7:15 PM")

    assertThat(
        Reservation(
            ReservationState.ACTIVE,
            "",
            null,
            Instant.parse("2023-02-03T19:15:30.00Z"),
            null
          )
          .line2Text(ZoneId.of("UTC"))
      )
      .isEqualTo("Device will expire at 7:15 PM")

    assertThat(
        Reservation(ReservationState.PENDING, "Connection pending", null, null, null)
          .line2Text(ZoneId.of("UTC"))
      )
      .isEqualTo("Connection pending")

    assertThat(
        Reservation(ReservationState.ACTIVE, "", null, null, null).line2Text(ZoneId.of("UTC"))
      )
      .isNull()
  }

  @Test
  fun icon() {
    val panel = DeviceNamePanel()
    val row = sampleRow().copy(icon = StudioIcons.DeviceExplorer.FIREBASE_DEVICE_CAR)

    panel.update(row)

    assertThat(panel.deviceIcon.baseIcon).isEqualTo(StudioIcons.DeviceExplorer.FIREBASE_DEVICE_CAR)
  }

  @Test
  fun wearPairing() {
    val panel = DeviceNamePanel()
    val row =
      DeviceRowData(
        template = null,
        handle = mock<DeviceHandle>(),
        name = "Pixel 6",
        type = DeviceType.HANDHELD,
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE,
        androidVersion = AndroidVersion(31),
        abi = Abi.ARM64_V8A,
        status = DeviceRowData.Status.ONLINE,
        error = null,
        handleType = DeviceRowData.HandleType.PHYSICAL,
        wearPairingId = "abcd1234",
        pairingStatus = emptyList()
      )
    panel.update(row)
    assertThat(panel.pairedLabel.isVisible).isFalse()

    panel.update(
      row.copy(
        pairingStatus =
          listOf(PairingStatus("watch2", "Pixel Watch", WearPairingManager.PairingState.CONNECTED))
      )
    )

    assertThat(panel.pairedLabel.isVisible).isTrue()
    assertThat(panel.pairedLabel.baseIcon)
      .isEqualTo(StudioIcons.DeviceExplorer.DEVICE_PAIRED_AND_CONNECTED)
    assertThat(panel.pairedLabel.accessibleContext.accessibleDescription).contains("Pixel Watch")
  }

  /** An arbitrary DeviceRowData that can easily be customized with copy(). */
  private fun sampleRow() =
    DeviceRowData(
      template = null,
      handle = mock<DeviceHandle>(),
      name = "Pixel 6",
      type = DeviceType.HANDHELD,
      icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE,
      androidVersion = AndroidVersion(31),
      abi = Abi.ARM64_V8A,
      status = DeviceRowData.Status.ONLINE,
      error = null,
      handleType = DeviceRowData.HandleType.PHYSICAL,
      wearPairingId = "abcd1234",
      pairingStatus = emptyList()
    )
}
