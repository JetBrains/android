/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataSourceTest {
  @Test
  fun `finds by id`() {
    assertThat(findDataSource("WEATHER.TEMPERATURE_UNIT"))
      .isEqualTo(StaticDataSource(id = "WEATHER.TEMPERATURE_UNIT", requiredVersion = WFFVersion2))
    assertThat(findDataSource("HEART_RATE"))
      .isEqualTo(StaticDataSource(id = "HEART_RATE", requiredVersion = WFFVersion1))
    assertThat(findDataSource("BATTERY_CHARGING_STATUS"))
      .isEqualTo(StaticDataSource(id = "BATTERY_CHARGING_STATUS", requiredVersion = WFFVersion1))

    assertThat(findDataSource("NON_EXISTING")).isNull()
  }

  @Test
  fun `finds by pattern`() {
    val patternedDataSource = findDataSource("WEATHER.HOURS.10.IS_AVAILABLE")
    assertThat(patternedDataSource).isInstanceOf(PatternedDataSource::class.java)
    assertThat(patternedDataSource?.requiredVersion).isEqualTo(WFFVersion2)

    assertThat(findDataSource("NON_EXISTING")).isNull()
    assertThat(findDataSource("WEATHER.HOURS.invalid.IS_AVAILABLE")).isNull()
  }
}
