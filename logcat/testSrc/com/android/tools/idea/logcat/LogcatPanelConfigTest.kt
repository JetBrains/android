/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.TagFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [LogcatPanelConfig]
 */
class LogcatPanelConfigTest {
  @Test
  fun formattingConfig_serializePreset() {
    for (style in FormattingOptions.Style.values()) {
      val preset = FormattingConfig.Preset(style)
      val config = logcatPanelConfig(formattingConfig = preset)

      val json = LogcatPanelConfig.toJson(config)
      val fromJson = LogcatPanelConfig.fromJson(json)

      assertThat(fromJson!!.formattingConfig).isEqualTo(preset)
    }
  }

  @Test
  fun formattingConfig_serializeCustom() {
    val custom = FormattingConfig.Custom(FormattingOptions(tagFormat = TagFormat(10)))
    val config = logcatPanelConfig(formattingConfig = custom)

    val json = LogcatPanelConfig.toJson(config)
    val fromJson = LogcatPanelConfig.fromJson(json)

    assertThat(fromJson!!.formattingConfig).isEqualTo(custom)
  }

  @Test
  fun restoreDeviceStateFromPreviousVersion_phisicalDevice() {
    val state = """
        {
          'device': {
            'serialNumber': 'HT85F1A00630',
            'name': 'google-pixel_2-HT85F1A00630',
            'isEmulator': false,
            'properties': {
              'ro.product.model': 'Pixel 2',
              'ro.product.manufacturer': 'Google',
              'ro.build.version.release': '11',
              'ro.build.version.sdk': '30'
            }
          },
          'formattingConfig': {
            'preset': 'STANDARD'
          },
          'filter': 'package:mine',
          'isSoftWrap': false
        }
    """.trimIndent()

    assertThat(LogcatPanelConfig.fromJson(state)?.device)
      .isEqualTo(Device.createPhysical("HT85F1A00630", false, "11", 30, "Google", "Pixel 2"))
  }

  @Test
  fun restoreDeviceStateFromPreviousVersion_emulator() {
    val state = """
      {
        'device': {
          'serialNumber': 'emulator-5554',
          'name': 'emulator-5554',
          'isEmulator': true,
          'avdName': 'Pixel_4_API_30',
          'properties': {
            'ro.product.model': 'sdk_gphone_x86',
            'ro.product.manufacturer': 'Google',
            'ro.build.version.release': '11',
            'ro.build.version.sdk': '30'
          }
        },
        'formattingConfig': {
          'preset': 'STANDARD'
        },
        'filter': 'package:mine',
        'isSoftWrap': false
      }
    """.trimIndent()

    assertThat(LogcatPanelConfig.fromJson(state)?.device)
      .isEqualTo(Device.createEmulator("emulator-5554", false, "11", 30, "Pixel_4_API_30"))
  }

  @Test
  fun restoreDeviceStateFromPreviousVersion_intsAsString() {
    val state = """
      {
        'device': {
          'deviceId': 'HT85F1A00630',
          'name': 'Google Pixel 2',
          'serialNumber': 'HT85F1A00630',
          'isOnline': false,
          'release': '11',
          'sdk': '30',
          'isEmulator': false,
          'model': 'Pixel 2'
        },
        'formattingConfig': {
          'preset': 'STANDARD'
        },
        'filter': 'tag:ActivityManager ',
        'isSoftWrap': false
      }    """.trimIndent()

    assertThat(LogcatPanelConfig.fromJson(state)?.device)
      .isEqualTo(Device.createPhysical("HT85F1A00630", false, "11", 30, "Google", "Pixel 2"))
  }

  @Test
  fun deserialize_missingModel() {
    val state = """{
      "device": {
        "deviceId": "0A091FDD4002XX",
        "name": "Google Pixel 5",
        "serialNumber": "0A091FDD4002XX",
        "isOnline": false,
        "release": 12,
        "sdk": 32,
        "isEmulator": false
      },
      "formattingConfig": {
        "preset": "STANDARD"
      },
      "filter": "name:\"My App\" package:mine",
      "isSoftWrap": false
    }""".trimIndent()

    assertThat(LogcatPanelConfig.fromJson(state)?.device)
      .isEqualTo(Device.createPhysical("0A091FDD4002XX", false, "12", 32, "Google", "Pixel 5").copy(model = ""))
  }

}

private fun logcatPanelConfig(
  device: Device? = null,
  formattingConfig: FormattingConfig = FormattingConfig.Preset(STANDARD),
  filter: String = "",
  isSoftWrap: Boolean = false,
) = LogcatPanelConfig(device, formattingConfig, filter, isSoftWrap)