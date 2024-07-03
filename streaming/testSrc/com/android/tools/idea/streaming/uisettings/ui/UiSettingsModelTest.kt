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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension

class UiSettingsModelTest {
  @Test
  fun api33PhoneHas4FontScales() {
    val model = UiSettingsModel(Dimension(1080, 2400), physicalDensity = 420, api = 33, DeviceType.HANDHELD)
    model.checkAvailableFontScales(FontScale.scaleMap.subList(0, 4))
  }

  @Test
  fun api34PhoneHas7FontScales() {
    val model = UiSettingsModel(Dimension(1080, 2400), physicalDensity = 420, api = 34, DeviceType.HANDHELD)
    model.checkAvailableFontScales(FontScale.scaleMap)
  }

  @Test
  fun wearHas6FontScales() {
    val model = UiSettingsModel(Dimension(360, 360), physicalDensity = 420, api = 33, DeviceType.WEAR)
    model.checkAvailableFontScales(WearFontScale.scaleMap)
  }

  private fun UiSettingsModel.checkAvailableFontScales(percentages: List<Int>) {
    assertThat(fontScaleMaxIndex.value).isEqualTo(percentages.size - 1)
    for ((index, percent) in percentages.withIndex()) {
      fontScaleIndex.setFromUi(index)
      assertThat(fontScaleInPercent.value).isEqualTo(percent)
    }
    fontScaleIndex.setFromUi(-1)
    assertThat(fontScaleInPercent.value).isEqualTo(percentages.first())
    fontScaleIndex.setFromUi(percentages.size + 1)
    assertThat(fontScaleInPercent.value).isEqualTo(percentages.last())
  }
}
