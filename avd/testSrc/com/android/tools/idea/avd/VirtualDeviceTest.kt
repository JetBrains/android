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

import com.android.SdkConstants
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.Storage
import com.android.sdklib.devices.VendorDevices
import com.android.sdklib.internal.avd.AvdBuilder
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.ColdBoot
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.InternalSdCard
import com.android.sdklib.internal.avd.OnDiskSkin
import com.android.sdklib.internal.avd.UserSettingsKey
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.utils.NullLogger
import com.google.common.truth.Truth.assertThat
import java.nio.file.Paths
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VirtualDeviceTest {
  @Test
  fun withDefaults() {
    val devices = VendorDevices(NullLogger()).apply { init() }
    val pixel8 = devices.getDevice("pixel_8", "Google")!!

    with(VirtualDevice.withDefaults(pixel8)) {
      assertThat(ram).isEqualTo(EmulatedProperties.defaultRamSize(device).toStorageCapacity())
      assertThat(vmHeapSize)
        .isEqualTo(EmulatedProperties.defaultVmHeapSize(device).toStorageCapacity())
      assertThat(internalStorage)
        .isEqualTo(EmulatedProperties.defaultInternalStorage(device).toStorageCapacity())
    }
  }

  @Test
  fun avdBuilderToVirtualDevice() {
    val devices = VendorDevices(NullLogger()).apply { init() }
    val pixel8 = devices.getDevice("pixel_8", "Google")!!

    val avdBuilder =
      AvdBuilder(Paths.get("/tmp/avd/pixel_8.ini"), Paths.get("/tmp/avd/pixel_8.avd"), pixel8)
    avdBuilder.systemImage = mockSystemImage()
    avdBuilder.displayName = "My Pixel"
    avdBuilder.sdCard = InternalSdCard(100 * 1024 * 1024L)
    avdBuilder.skin = OnDiskSkin(Paths.get("pixel_8"))
    avdBuilder.screenOrientation = ScreenOrientation.LANDSCAPE
    avdBuilder.cpuCoreCount = 2
    avdBuilder.ram = Storage(16, Storage.Unit.GiB)
    avdBuilder.vmHeap = Storage(500, Storage.Unit.MiB)
    avdBuilder.internalStorage = Storage(128, Storage.Unit.GiB)
    avdBuilder.frontCamera = AvdCamera.WEBCAM
    avdBuilder.backCamera = AvdCamera.EMULATED
    avdBuilder.gpuMode = GpuMode.AUTO
    avdBuilder.networkLatency = AvdNetworkLatency.GPRS
    avdBuilder.networkSpeed = AvdNetworkSpeed.GSM
    avdBuilder.bootMode = ColdBoot
    avdBuilder.userSettings[UserSettingsKey.PREFERRED_ABI] = SdkConstants.ABI_RISCV64

    with(VirtualDevice.withDefaults(pixel8).copyFrom(avdBuilder)) {
      assertThat(device).isEqualTo(pixel8)
      assertThat(name).isEqualTo("My Pixel")
      assertThat(expandedStorage).isEqualTo(Custom(StorageCapacity(100, StorageCapacity.Unit.MB)))
      assertThat(skin.path().toString()).isEqualTo("pixel_8")
      assertThat(orientation).isEqualTo(ScreenOrientation.LANDSCAPE)
      assertThat(cpuCoreCount).isEqualTo(2)
      assertThat(ram).isEqualTo(StorageCapacity(16, StorageCapacity.Unit.GB))
      assertThat(vmHeapSize).isEqualTo(StorageCapacity(500, StorageCapacity.Unit.MB))
      assertThat(internalStorage).isEqualTo(StorageCapacity(128, StorageCapacity.Unit.GB))
      assertThat(frontCamera).isEqualTo(AvdCamera.WEBCAM)
      assertThat(rearCamera).isEqualTo(AvdCamera.EMULATED)
      assertThat(graphicsMode).isEqualTo(GraphicsMode.AUTO)
      assertThat(latency).isEqualTo(AvdNetworkLatency.GPRS)
      assertThat(speed).isEqualTo(AvdNetworkSpeed.GSM)
      assertThat(defaultBoot).isEqualTo(Boot.COLD)
      assertThat(preferredAbi).isEqualTo(SdkConstants.ABI_RISCV64)
    }
  }

  @Test
  fun virtualDeviceToAvdBuilder() {
    val devices = VendorDevices(NullLogger()).apply { init() }
    val pixel8 = devices.getDevice("pixel_8", "Google")!!
    val avdBuilder =
      AvdBuilder(Paths.get("/tmp/avd/pixel_8.ini"), Paths.get("/tmp/avd/pixel_8.avd"), pixel8)

    val device =
      VirtualDevice(
        device = pixel8,
        name = "My Pixel",
        expandedStorage = Custom(StorageCapacity(100, StorageCapacity.Unit.MB)),
        skin = DefaultSkin(Paths.get("pixel_8")),
        orientation = ScreenOrientation.LANDSCAPE,
        cpuCoreCount = 2,
        ram = StorageCapacity(16, StorageCapacity.Unit.GB),
        vmHeapSize = StorageCapacity(500, StorageCapacity.Unit.MB),
        internalStorage = StorageCapacity(128, StorageCapacity.Unit.GB),
        frontCamera = AvdCamera.WEBCAM,
        rearCamera = AvdCamera.EMULATED,
        graphicsMode = GraphicsMode.AUTO,
        latency = AvdNetworkLatency.GPRS,
        speed = AvdNetworkSpeed.GSM,
        defaultBoot = Boot.COLD,
        preferredAbi = SdkConstants.ABI_RISCV64,
      )

    avdBuilder.copyFrom(device, mockSystemImage())

    with(avdBuilder) {
      assertThat(displayName).isEqualTo("My Pixel")
      assertThat(sdCard).isEqualTo(InternalSdCard(100 * 1024 * 1024L))
      assertThat(skin).isEqualTo(OnDiskSkin(Paths.get("pixel_8")))
      assertThat(screenOrientation).isEqualTo(ScreenOrientation.LANDSCAPE)
      assertThat(cpuCoreCount).isEqualTo(2)
      assertThat(ram).isEqualTo(Storage(16, Storage.Unit.GiB))
      assertThat(vmHeap).isEqualTo(Storage(500, Storage.Unit.MiB))
      assertThat(internalStorage).isEqualTo(Storage(128, Storage.Unit.GiB))
      assertThat(frontCamera).isEqualTo(AvdCamera.WEBCAM)
      assertThat(backCamera).isEqualTo(AvdCamera.EMULATED)
      assertThat(gpuMode).isEqualTo(GpuMode.AUTO)
      assertThat(networkLatency).isEqualTo(AvdNetworkLatency.GPRS)
      assertThat(networkSpeed).isEqualTo(AvdNetworkSpeed.GSM)
      assertThat(bootMode).isEqualTo(ColdBoot)
      assertThat(userSettings[UserSettingsKey.PREFERRED_ABI]).isEqualTo(SdkConstants.ABI_RISCV64)
    }
  }

  private fun mockSystemImage(): ISystemImage =
    mock<ISystemImage>().apply { whenever(androidVersion).thenReturn(AndroidVersion(34)) }
}
