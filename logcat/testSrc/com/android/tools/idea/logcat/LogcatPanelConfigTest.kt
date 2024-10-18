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
}

private fun logcatPanelConfig(
  device: Device? = null,
  file: String? = null,
  formattingConfig: FormattingConfig = FormattingConfig.Preset(STANDARD),
  filter: String = "",
  filterMatchCase: Boolean = false,
  isSoftWrap: Boolean = false,
) = LogcatPanelConfig(device, file, formattingConfig, filter, filterMatchCase, isSoftWrap)
