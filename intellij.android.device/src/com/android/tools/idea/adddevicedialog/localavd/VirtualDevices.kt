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
package com.android.tools.idea.adddevicedialog.localavd

import com.android.resources.ScreenOrientation
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.avdmanager.ui.AvdWizardUtils
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks

internal object VirtualDevices {
  internal fun add(device: VirtualDevice) {
    val connection = AvdManagerConnection.getDefaultAvdManagerConnection()
    val id = AvdWizardUtils.cleanAvdName(connection, device.name, /* uniquify= */ true)

    val definition =
      DeviceManagerConnection.getDefaultDeviceManagerConnection().devices.first {
        it.id == "pixel_6"
      }

    val image =
      AndroidSdks.getInstance()
        .tryToChooseSdkHandler()
        .getSystemImageManager(StudioLoggerProgressIndicator(VirtualDevices::class.java))
        .images
        .first { it.`package`.path == "system-images;android-34;google_apis;x86_64" }

    val skin = device.skin.path()

    val properties =
      mutableMapOf(
        "AvdId" to id,
        "avd.ini.displayname" to device.name,
        "disk.dataPartition.size" to device.internalStorage.withMaxUnit().toString(),
        "hw.accelerometer" to "yes",
        "hw.audioInput" to "yes",
        "hw.battery" to "yes",
        "hw.camera.back" to device.rearCamera.asParameter,
        "hw.camera.front" to device.frontCamera.asParameter,
        "hw.dPad" to "no",
        "hw.device.hash2" to "MD5:3db3250dab5d0d93b29353040181c7e9",
        "hw.device.manufacturer" to "Google",
        "hw.device.name" to "pixel_6",
        "hw.gps" to "yes",
        // TODO This depends on the system image and device.graphicAcceleration. See
        //   ConfigureAvdOptionsStep.java.
        "hw.gpu.enabled" to "yes",
        // TODO Older emulators expect "guest" instead of "software". See AvdOptionsModel.java.
        "hw.gpu.mode" to device.graphicAcceleration.gpuSetting,
        "hw.initialOrientation" to ScreenOrientation.PORTRAIT.shortDisplayValue.lowercase(),
        "hw.keyboard" to "yes",
        "hw.lcd.density" to "420",
        "hw.lcd.height" to "2400",
        "hw.lcd.width" to "1080",
        "hw.mainKeys" to "no",
        "hw.ramSize" to device.simulatedRam.valueIn(StorageCapacity.Unit.MB).toString(),
        "hw.sdCard" to if (device.expandedStorage == None) "no" else "yes",
        "hw.sensors.orientation" to "yes",
        "hw.sensors.proximity" to "yes",
        "hw.trackBall" to "no",
        "runtime.network.latency" to device.latency.asParameter,
        "runtime.network.speed" to device.speed.asParameter,
        "showDeviceFrame" to "yes",
        "skin.dynamic" to "yes",
        "skin.path" to skin.toString(),
        "vm.heapSize" to device.vmHeapSize.valueIn(StorageCapacity.Unit.MB).toString(),
      )

    properties.putAll(device.defaultBoot.properties)

    if (device.cpuCoreCount != null) {
      properties["hw.cpu.ncore"] = device.cpuCoreCount.toString()
    }

    connection.createOrUpdateAvd(
      /* currentInfo= */ null,
      /* avdName= */ id,
      /* device= */ definition,
      /* systemImageDescription= */ SystemImageDescription(image),
      /* orientation= */ device.orientation,
      /* isCircular= */ false,
      /* sdCard= */ device.expandedStorage.toString().ifEmpty { null },
      /* skinFolder= */ skin,
      /* hardwareProperties= */ properties,
      /* userSettings= */ null,
      /* removePrevious= */ true,
    )
  }
}
