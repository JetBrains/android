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

import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.TagFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [LogcatPanelConfig] */
class LogcatPanelConfigTest {
  @Test
  fun formattingConfig_serializePreset() {
    for (style in FormattingOptions.Style.entries) {
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
  fun missingFormattingConfig() {
    val state =
      """
      {
        'filter': 'tag:ActivityManager ',
        'isSoftWrap': false
      }"""
        .trimIndent()

    assertThat(LogcatPanelConfig.fromJson(state)).isNull()
  }

  @Test
  fun badDevice() {
    val state =
      """
      {
        'device': {
          'foobar': '1'
        },
        'formattingConfig': {
          'preset': 'STANDARD'
        },
        'filter': 'tag:ActivityManager ',
        'isSoftWrap': false
      }"""
        .trimIndent()

    assertThat(LogcatPanelConfig.fromJson(state))
      .isEqualTo(
        LogcatPanelConfig(
          device = null,
          file = null,
          formattingConfig = FormattingConfig.Preset(STANDARD),
          filter = "tag:ActivityManager ",
          isSoftWrap = false,
          filterMatchCase = false,
          proguardFile = null,
        )
      )
  }

  @Test
  fun deviceRoundtrip() {
    val device =
      Device(
        deviceId = "id",
        name = "name",
        serialNumber = "serial",
        isOnline = true,
        release = "Release",
        apiLevel = AndroidApiLevel(77, 7),
        featureLevel = 35,
        model = "Model",
        type = DeviceType.HANDHELD,
      )
    val config = logcatPanelConfig(device = device)

    val json = LogcatPanelConfig.toJson(config)
    val fromJson = LogcatPanelConfig.fromJson(json)

    assertThat(fromJson!!.device).isEqualTo(device)
  }
}

private fun logcatPanelConfig(
  device: Device? = null,
  file: String? = null,
  formattingConfig: FormattingConfig = FormattingConfig.Preset(STANDARD),
  filter: String = "",
  filterMatchCase: Boolean = false,
  isSoftWrap: Boolean = false,
  proguardFile: String? = null,
) =
  LogcatPanelConfig(
    device,
    file,
    formattingConfig,
    filter,
    filterMatchCase,
    isSoftWrap,
    proguardFile,
  )
