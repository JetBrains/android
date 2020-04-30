/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.testutils.TestUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.createDirectories
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE

class FakeEmulator(val avdFolder: Path, val grpcPort: Int, configuration: Configuration) {

  private val avdId = StringUtil.trimExtensions(avdFolder.fileName.toString())
  private val registration = """
      port.serial=${grpcPort - 3000}
      port.adb=${grpcPort - 3000 + 1}
      avd.name=${avdId}
      avd.dir=${avdFolder}
      avd.id=${avdId}
      cmdline="/emulator_home/fake_emulator" "-netdelay" "none" "-netspeed" "full" "-avd" "${avdId}" "-no-window" "-gpu" "auto-no-window"
      grpc.port=${grpcPort}
      grpc.certificate=
      """.trimIndent()
  private val registrationFile = configuration.emulatorRegistrationDirectory.resolve("pid_${grpcPort + 12345}.ini")

  /**
   * Starts the Emulator. The Emulator is fully initialized when the method returns.
   */
  fun start() {
    Files.write(registrationFile, registration.toByteArray(UTF_8), CREATE)
  }

  /**
   * Stops the Emulator. The Emulator is completely shut down when the method returns.
   */
  fun stop() {
    Files.delete(registrationFile)
  }

  companion object {
    @JvmStatic
    fun createPhoneAvd(parentFolder: Path): Path {
      val avdId = "Pixel_3_XL_API_29"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val skinFolder = getSkinFolder("pixel_3_xl")

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=false
          abi.type=x86
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=800M
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=virtualscene
          hw.camera.front=emulated
          hw.cpu.arch=x86
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name=Pixel 3 XL
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=Portrait
          hw.keyboard=yes
          hw.lcd.density=480
          hw.lcd.height=2960
          hw.lcd.width=1440
          hw.mainKeys=no
          hw.ramSize=1536
          hw.sdCard=yes
          hw.sensors.orientation=yes
          hw.sensors.proximity=yes
          hw.trackBall=no
          image.sysdir.1=system-images/android-29/google_apis/x86/
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.path=${avdFolder}/sdcard.img
          sdcard.size=512 MB
          showDeviceFrame=yes
          skin.dynamic=yes
          skin.name=${skinFolder.fileName}
          skin.path=${skinFolder}
          tag.display=Google APIs
          tag.id=google_apis
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86
          hw.cpu.model = qemu32
          hw.cpu.ncore = 4
          hw.ramSize = 1536
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = false
          hw.accelerometer = false
          hw.gyroscope = true
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = false
          """.trimIndent()

      return createAvd(avdFolder, configIni, hardwareIni)
    }

    @JvmStatic
    fun createWatchAvd(parentFolder: Path): Path {
      val avdId = "Android_Wear_Round_API_28"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val skinFolder = getSkinFolder("wear_round")

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=true
          abi.type=x86
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=2G
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=None
          hw.camera.front=None
          hw.cpu.arch=x86
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name=wear_round
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=Portrait
          hw.keyboard=yes
          hw.keyboard.lid=yes
          hw.lcd.density=240
          hw.lcd.height=320
          hw.lcd.width=320
          hw.mainKeys=yes
          hw.ramSize=512
          hw.sdCard=yes
          hw.sensors.orientation=yes
          hw.sensors.proximity=yes
          hw.trackBall=no
          image.sysdir.1=system-images/android-28/android-wear/x86/
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.size=512M
          showDeviceFrame=yes
          skin.dynamic=yes
          skin.name=${skinFolder.fileName}
          skin.path=${skinFolder}
          tag.display=Wear OS
          tag.id=android-wear
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86
          hw.cpu.model = qemu32
          hw.cpu.ncore = 4
          hw.ramSize = 1536
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = false
          hw.accelerometer = false
          hw.gyroscope = true
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = true
          hw.sdCard.path = ${avdFolder}/sdcard.img
          """.trimIndent()

      return createAvd(avdFolder, configIni, hardwareIni)
    }

    @JvmStatic
    private fun createAvd(avdFolder: Path, configIni: String, hardwareIni: String): Path {
      avdFolder.createDirectories()
      Files.write(avdFolder.resolve("config.ini"), configIni.toByteArray(UTF_8))
      Files.write(avdFolder.resolve("hardware-qemu.ini"), hardwareIni.toByteArray(UTF_8))
      return avdFolder
    }

    @JvmStatic
    fun getSkinFolder(skinName: String): Path {
      return TestUtils.getWorkspaceRoot().toPath().resolve("tools/adt/idea/artwork/resources/device-art-resources/${skinName}")
    }
  }
}