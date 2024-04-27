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
package com.android.tools.idea.adddevicedialog

import com.android.resources.ScreenOrientation
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.AvdWizardUtils
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import java.nio.file.Path

internal object VirtualDevices {
  internal fun add(device: VirtualDevice) {
    val handler = AndroidSdks.getInstance().tryToChooseSdkHandler()

    val connection = AvdManagerConnection.getDefaultAvdManagerConnection()
    val id = AvdWizardUtils.cleanAvdName(connection, device.name, /* uniquify= */ true)

    val definition =
      DeviceManagerConnection.getDefaultDeviceManagerConnection().devices.first {
        it.id == "pixel_7"
      }

    val image =
      handler
        .getSystemImageManager(StudioLoggerProgressIndicator(VirtualDevices::class.java))
        .images
        .first { it.`package`.path == "system-images;android-34;google_apis_playstore;x86_64" }

    val skin = Path.of(handler.location.toString(), "skins", "pixel_7")

    val properties =
      mapOf(
        "AvdId" to id,
        "avd.ini.displayname" to device.name,
        "disk.dataPartition.size" to "2G",
        "fastboot.chosenSnapshotFile" to "",
        "fastboot.forceChosenSnapshotBoot" to "no",
        "fastboot.forceColdBoot" to "no",
        "fastboot.forceFastBoot" to "yes",
        "hw.accelerometer" to "yes",
        "hw.audioInput" to "yes",
        "hw.battery" to "yes",
        "hw.camera.back" to "virtualscene",
        "hw.camera.front" to "emulated",
        "hw.cpu.ncore" to "4",
        "hw.dPad" to "no",
        "hw.device.hash2" to "MD5:3db3250dab5d0d93b29353040181c7e9",
        "hw.device.manufacturer" to "Google",
        "hw.device.name" to "pixel_7",
        "hw.gps" to "yes",
        "hw.gpu.enabled" to "yes",
        "hw.gpu.mode" to "auto",
        "hw.initialOrientation" to "Portrait",
        "hw.keyboard" to "yes",
        "hw.lcd.density" to "420",
        "hw.lcd.height" to "2400",
        "hw.lcd.width" to "1080",
        "hw.mainKeys" to "no",
        "hw.ramSize" to "2048",
        "hw.sdCard" to "yes",
        "hw.sensors.orientation" to "yes",
        "hw.sensors.proximity" to "yes",
        "hw.trackBall" to "no",
        "runtime.network.latency" to "none",
        "runtime.network.speed" to "full",
        "showDeviceFrame" to "yes",
        "skin.dynamic" to "yes",
        "skin.path" to skin.toString(),
        "vm.heapSize" to "256"
      )

    connection.createOrUpdateAvd(
      /* currentInfo= */ null,
      /* avdName= */ id,
      /* device= */ definition,
      /* systemImageDescription= */ SystemImageDescription(image),
      /* orientation= */ ScreenOrientation.PORTRAIT,
      /* isCircular= */ false,
      /* sdCard= */ "512M",
      /* skinFolder= */ skin,
      /* hardwareProperties= */ properties,
      /* removePrevious= */ true
    )
  }
}
