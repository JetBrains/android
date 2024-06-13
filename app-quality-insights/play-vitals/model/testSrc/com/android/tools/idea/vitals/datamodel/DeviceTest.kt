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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.DeviceType
import com.google.common.truth.Truth.assertThat
import com.google.play.developer.reporting.DeviceId
import com.google.play.developer.reporting.DeviceModelSummary
import org.junit.Test

class DeviceTest {

  @Test
  fun `device from proto`() {
    val device1 =
      DeviceModelSummary.newBuilder()
        .apply {
          deviceId =
            DeviceId.newBuilder()
              .apply {
                buildBrand = "samsung"
                buildDevice = "blqt"
              }
              .build()
          marketingName = "samsung blqt (Galaxy A7)"
        }
        .build()

    val device2 =
      DeviceModelSummary.newBuilder()
        .apply {
          deviceId =
            DeviceId.newBuilder()
              .apply {
                buildBrand = "samsung"
                buildDevice = "blqt"
              }
              .build()
          marketingName = "samsung blqt (SAMSUNG Galaxy A7)"
        }
        .build()

    val device3 =
      DeviceModelSummary.newBuilder()
        .apply {
          deviceId =
            DeviceId.newBuilder()
              .apply {
                buildBrand = "unknown"
                buildDevice = "unknown"
              }
              .build()
          marketingName = "unknown"
        }
        .build()

    assertThat(Device.fromProto(device1)).isEqualTo(Device("samsung", "blqt", "samsung Galaxy A7"))
    assertThat(Device.fromProto(device2)).isEqualTo(Device("samsung", "blqt", "SAMSUNG Galaxy A7"))
    assertThat(Device.fromProto(device3)).isEqualTo(Device("unknown", "unknown", "unknown"))
  }

  @Test
  fun `device from dimension`() {
    val dimension =
      listOf(
        Dimension(DimensionType.DEVICE_BRAND, DimensionValue.StringValue("samsung"), "samsung"),
        Dimension(
          DimensionType.DEVICE_MODEL,
          DimensionValue.StringValue("blqt"),
          "samsung blqt (Galaxy A7)",
        ),
        Dimension(DimensionType.DEVICE_TYPE, DimensionValue.StringValue("phone"), "phone"),
      )

    val unknown =
      listOf(
        Dimension(DimensionType.DEVICE_BRAND, DimensionValue.StringValue("unknown"), "unknown"),
        Dimension(DimensionType.DEVICE_MODEL, DimensionValue.StringValue("unknown"), ""),
      )

    assertThat(Device.fromDimensions(dimension))
      .isEqualTo(Device("samsung", "blqt", "Galaxy A7", DeviceType("phone")))
    assertThat(Device.fromDimensions(unknown))
      .isEqualTo(Device("unknown", "unknown", "", DeviceType("")))
  }
}
