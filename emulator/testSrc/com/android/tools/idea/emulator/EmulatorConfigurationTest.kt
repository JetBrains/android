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

import com.android.emulator.control.EntryList
import com.android.tools.idea.protobuf.TextFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Paths

/**
 * Tests for [EmulatorConfiguration].
 */
class EmulatorConfigurationTest {
  @Test
  fun testValidConfiguration() {
    val proto = """
      entry {
        key: "hw.audioOutput"
        value: "true"
      }
      entry {
        key: "hw.lcd.width"
        value: "1440"
      }
      entry {
        key: "hw.lcd.height"
        value: "2960"
      }
      entry {
        key: "hw.sensors.orientation"
        value: "true"
      }
      entry {
        key: "avd.name"
        value: "Pixel_3_XL_API_29"
      }
      entry {
        key: "avd.id"
        value: "Pixel_3_XL_API_29"
      }
      entry {
        key: "android.avd.home"
        value: "/home/username/.android/avd"
      }
      """.trimIndent()
    val entryList = parseEntryList(proto)
    val config = EmulatorConfiguration.fromHardwareConfig(entryList)
    assertThat(config).isNotNull()
    assertThat(config?.avdId).isEqualTo("Pixel_3_XL_API_29")
    assertThat(config?.avdName).isEqualTo("Pixel 3 XL API 29")
    assertThat(config?.avdPath).isEqualTo(Paths.get("/home/username/.android/avd/Pixel_3_XL_API_29"))
    assertThat(config?.displayWidth).isEqualTo(1440)
    assertThat(config?.displayHeight).isEqualTo(2960)
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
  }

  @Test
  fun testValidConfigurationMissingNonessentialData() {
    val proto = """
      entry {
        key: "hw.lcd.width"
        value: "1152"
      }
      entry {
        key: "hw.lcd.height"
        value: "1536"
      }
      entry {
        key: "hw.sensors.orientation"
        value: "false"
      }
      entry {
        key: "avd.name"
        value: "Polestar_2_API_28"
      }
      entry {
        key: "avd.id"
        value: "Polestar_2_API_28"
      }
      entry {
        key: "android.avd.home"
        value: "/home/username/.android/avd"
      }
      """.trimIndent()
    val entryList = parseEntryList(proto)
    val config = EmulatorConfiguration.fromHardwareConfig(entryList)
    assertThat(config).isNotNull()
    assertThat(config?.avdId).isEqualTo("Polestar_2_API_28")
    assertThat(config?.avdName).isEqualTo("Polestar 2 API 28")
    assertThat(config?.avdPath).isEqualTo(Paths.get("/home/username/.android/avd/Polestar_2_API_28"))
    assertThat(config?.displayWidth).isEqualTo(1152)
    assertThat(config?.displayHeight).isEqualTo(1536)
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isFalse()
  }

  @Test
  fun testInvalidConfiguration() {
    val proto = """
      entry {
        key: "hw.audioOutput"
        value: "true"
      }
      entry {
        key: "hw.lcd.width"
        value: "1440"
      }
      entry {
        key: "hw.lcd.height"
        value: "2960"
      }
      entry {
        key: "hw.sensors.orientation"
        value: "true"
      }
      entry {
        key: "avd.id"
        value: "Pixel_3_XL_API_29"
      }
      entry {
        key: "android.avd.home"
        value: "/home/username/.android/avd"
      }
      """.trimIndent()
    val entryList = parseEntryList(proto)
    val config = EmulatorConfiguration.fromHardwareConfig(entryList)
    assertThat(config).isNull()
  }

  private fun parseEntryList(asciiProto: String): EntryList {
    val builder = EntryList.newBuilder()
    TextFormat.merge(asciiProto, builder)
    return builder.build()
  }
}