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

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.createDirectories
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/**
 * Tests for [EmulatorConfiguration].
 */
class EmulatorConfigurationTest {
  @Test
  fun testReadAvdDefinition() {
    val baseDir = if (SystemInfo.isWindows) "C:/home/janedoe" else "/home/janedoe"
    val configIni = """
        AvdId=Pixel_3_XL_API_29
        PlayStore.enabled=false
        abi.type=x86
        avd.ini.displayname=Custom name
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
        sdcard.path=${baseDir}/.android/avd/Pixel_3_XL_API_29.avd/sdcard.img
        sdcard.size=512 MB
        showDeviceFrame=yes
        skin.dynamic=yes
        skin.name=pixel_3_xl
        skin.path=${baseDir}/Android/Sdk/skins/pixel_3_xl
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

    // Prepare.
    val fileSystem = Jimfs.newFileSystem()
    val avdFolder = fileSystem.getPath("${baseDir}/.android/avd/Pixel_3_XL_API_29.avd")
    avdFolder.createDirectories()
    Files.write(avdFolder.resolve("config.ini"), configIni.toByteArray(UTF_8))
    Files.write(avdFolder.resolve("hardware-qemu.ini"), hardwareIni.toByteArray(UTF_8))

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition("Pixel_3_XL_API_29", avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Custom name")
    assertThat(config?.displayWidth).isEqualTo(1440)
    assertThat(config?.displayHeight).isEqualTo(2960)
    assertThat(config?.density).isEqualTo(480)
    assertThat(config?.skinFolder).isEqualTo(fileSystem.getPath("${baseDir}/Android/Sdk/skins/pixel_3_xl"))
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
  }
}