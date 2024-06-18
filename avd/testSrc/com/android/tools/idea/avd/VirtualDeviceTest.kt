/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import org.junit.Test

class VirtualDeviceTest {
  @Test
  fun builder() {
    val device =
      VirtualDevice(
        deviceId = "round_phone",
        name = "RoundPhone",
        manufacturer = "BlueBerry",
        apiRange = Range.closed(21, 34),
        formFactor = FormFactors.PHONE,
        sdkExtensionLevel = AndroidVersion(34, null, 7, true),
        skin = DefaultSkin(Path.of("/tmp/skin")),
        frontCamera = AvdCamera.EMULATED,
        rearCamera = AvdCamera.VIRTUAL_SCENE,
        speed = EmulatedProperties.DEFAULT_NETWORK_SPEED,
        latency = EmulatedProperties.DEFAULT_NETWORK_LATENCY,
        orientation = ScreenOrientation.PORTRAIT,
        defaultBoot = Boot.QUICK,
        internalStorage = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        expandedStorage = Custom(StorageCapacity(512, StorageCapacity.Unit.MB)),
        cpuCoreCount = EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES,
        graphicAcceleration = GpuMode.AUTO,
        simulatedRam = StorageCapacity(2_048, StorageCapacity.Unit.MB),
        vmHeapSize = StorageCapacity(256, StorageCapacity.Unit.MB),
        abis = listOf(Abi.ARM64_V8A),
        resolution = Resolution(1200, 800),
        displayDensity = 200,
        displayDiagonalLength = 6.2,
        isRound = true,
      )

    assertThat(device.toBuilder().build()).isEqualTo(device)
    assertThat(device.update { graphicAcceleration = GpuMode.HOST }.graphicAcceleration)
      .isEqualTo(GpuMode.HOST)
  }
}
