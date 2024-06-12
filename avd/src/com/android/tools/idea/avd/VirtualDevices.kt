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
package com.android.tools.idea.avd

import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.targets.SystemImageManager
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.avdmanager.ui.AvdWizardUtils
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks

internal class VirtualDevices(
  val devices: List<Device> =
    DeviceManagerConnection.getDefaultDeviceManagerConnection().devices.toList(),
  val avdManagerConnection: AvdManagerConnection =
    AvdManagerConnection.getDefaultAvdManagerConnection(),
  private val manager: SystemImageManager = getSystemImageManager(),
) {
  internal fun add(device: VirtualDevice, image: SystemImage) {
    // First, the defaults to use if the device definition doesn't specify them.
    val properties =
      mutableMapOf("hw.keyboard" to "no", "skin.dynamic" to "yes", "showDeviceFrame" to "yes")

    val definition = device.device

    val id = AvdWizardUtils.cleanAvdName(avdManagerConnection, device.name, /* uniquify= */ true)
    val skin = device.skin.path()

    val sdklibImage = manager.images.first { it.`package`.path == image.path }

    // Next, the values from device definition.
    properties.putAll(DeviceManager.getHardwareProperties(definition))

    // Finally, the user's inputs.
    properties.putAll(
      mapOf(
        "AvdId" to id,
        "avd.ini.displayname" to device.name,
        "disk.dataPartition.size" to device.internalStorage.withMaxUnit().toString(),
        "hw.camera.back" to device.rearCamera.asParameter,
        "hw.camera.front" to device.frontCamera.asParameter,
        // TODO This depends on the system image and device.graphicAcceleration. See
        //   ConfigureAvdOptionsStep.java.
        "hw.gpu.enabled" to "yes",
        // TODO Older emulators expect "guest" instead of "software". See AvdOptionsModel.java.
        "hw.gpu.mode" to device.graphicAcceleration.gpuSetting,
        "hw.initialOrientation" to ScreenOrientation.PORTRAIT.shortDisplayValue.lowercase(),
        "hw.ramSize" to device.simulatedRam.valueIn(StorageCapacity.Unit.MB).toString(),
        "hw.sdCard" to if (device.expandedStorage == None) "no" else "yes",
        "runtime.network.latency" to device.latency.asParameter,
        "runtime.network.speed" to device.speed.asParameter,
        "skin.path" to skin.toString(),
        "vm.heapSize" to device.vmHeapSize.valueIn(StorageCapacity.Unit.MB).toString(),
      )
    )
    properties.putAll(device.defaultBoot.properties)
    if (device.cpuCoreCount != null) {
      properties["hw.cpu.ncore"] = device.cpuCoreCount.toString()
    }

    avdManagerConnection.createOrUpdateAvd(
      /* currentInfo= */ null,
      /* avdName= */ id,
      /* device= */ definition,
      /* systemImageDescription= */ SystemImageDescription(sdklibImage),
      /* orientation= */ device.orientation,
      /* isCircular= */ false,
      /* sdCard= */ null, // TODO(341790805): pass SD card settings
      /* skinFolder= */ skin,
      /* hardwareProperties= */ properties,
      /* userSettings= */ null,
      /* removePrevious= */ true,
    )
  }

  private companion object {
    private fun getSystemImageManager() =
      AndroidSdks.getInstance()
        .tryToChooseSdkHandler()
        .getSystemImageManager(StudioLoggerProgressIndicator(VirtualDevices::class.java))
  }
}
