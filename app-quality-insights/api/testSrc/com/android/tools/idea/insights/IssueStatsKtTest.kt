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
package com.android.tools.idea.insights

import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Test

class IssueStatsKtTest {
  @Test
  fun `summarize devices from raw data points`() {
    val result =
      RAW_DEVICE_DATA_POINTS.summarizeDevicesFromRawDataPoints(3, 10.0)
        ?.map(
          Double::roundToInt
        ) // map to int to avoid float equalities since they are not precise.

    assertThat(result)
      .isEqualTo(
        IssueStats(
          topValue = "Galaxy A51",
          groups =
            listOf(
              StatsGroup(
                "samsung",
                54,
                breakdown =
                  listOf(
                    DataPoint("Galaxy A51", 11),
                    DataPoint("Galaxy S10", 11),
                    DataPoint("Galaxy A52", 10),
                    DataPoint("Galaxy S8", 10),
                    DataPoint("Other", 12),
                  ),
              ),
              StatsGroup(
                "Xiaomi",
                22,
                breakdown =
                  listOf(
                    DataPoint("Redmi Note 8 Pro", 11),
                    DataPoint("POCO X3 Pro", 1),
                    DataPoint("Redmi  Note  7", 1),
                    DataPoint("Other", 10),
                  ),
              ),
              StatsGroup(
                "HUAWEI",
                13,
                breakdown =
                  listOf(
                    DataPoint("P30 Pro", 11),
                    DataPoint("HUAWEI P30 lite", 0),
                    DataPoint("Honor 10", 0),
                    DataPoint("Other", 2),
                  ),
              ),
              StatsGroup(
                "Google",
                10,
                breakdown =
                  listOf(
                    DataPoint("Pixel 4a", 5),
                    DataPoint("Pixel 2 XL", 1),
                    DataPoint("Pixel 6", 1),
                    DataPoint("Other", 4),
                  ),
              ),
              StatsGroup(
                "Other",
                1,
                breakdown = listOf(DataPoint("OnePlus", 1), DataPoint("realme", 0)),
              ),
            ),
        )
      )
  }

  @Test
  fun `summarize Oses from raw data points`() {
    val result =
      RAW_OS_DATA_POINTS.summarizeOsesFromRawDataPoints(3, 10.0)
        ?.map(
          Double::roundToInt
        ) // map to int to avoid float equalities since they are not precise.

    assertThat(result)
      .isEqualTo(
        IssueStats(
          topValue = "Android (12)",
          groups =
            listOf(
              StatsGroup("Android (12)", 50, breakdown = emptyList()),
              StatsGroup("Android (11)", 18, breakdown = emptyList()),
              StatsGroup("Android (10)", 15, breakdown = emptyList()),
              StatsGroup("Android (9)", 15, breakdown = emptyList()),
              StatsGroup(
                "Other",
                2,
                breakdown =
                  listOf(
                    DataPoint(name = "Android (8)", percentage = 1),
                    DataPoint(name = "Android (7)", percentage = 1),
                  ),
              ),
            ),
        )
      )
  }
}

private val RAW_OS_DATA_POINTS =
  listOf(
    WithCount(
      value = OperatingSystemInfo(displayVersion = "12", displayName = "Android (12)"),
      count = 500,
    ),
    WithCount(
      value = OperatingSystemInfo(displayVersion = "11", displayName = "Android (11)"),
      count = 180,
    ),
    WithCount(
      value = OperatingSystemInfo(displayVersion = "10", displayName = "Android (10)"),
      count = 150,
    ),
    WithCount(
      value = OperatingSystemInfo(displayVersion = "9", displayName = "Android (9)"),
      count = 150,
    ),
    WithCount(
      value = OperatingSystemInfo(displayVersion = "8", displayName = "Android (8)"),
      count = 10,
    ),
    WithCount(
      value = OperatingSystemInfo(displayVersion = "7", displayName = "Android (7)"),
      count = 10,
    ),
  )

val RAW_DEVICE_DATA_POINTS =
  listOf(
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A51", displayName = ""),
      count = 6600,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "P30 Pro", displayName = ""),
      count = 6501,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 8 Pro", displayName = ""),
      count = 6499,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S10", displayName = ""),
      count = 6498,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A52", displayName = ""),
      count = 6433,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S8", displayName = ""),
      count = 6433,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 4a", displayName = ""),
      count = 2905,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "POCO X3 Pro", displayName = ""),
      count = 545,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi  Note  7", displayName = ""),
      count = 478,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 9 Pro", displayName = ""),
      count = 474,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 10 Pro", displayName = ""),
      count = 463,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 2 XL", displayName = ""),
      count = 461,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A32", displayName = ""),
      count = 432,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi 9", displayName = ""),
      count = 430,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S20 FE", displayName = ""),
      count = 426,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 9", displayName = ""),
      count = 419,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 6", displayName = ""),
      count = 410,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A50", displayName = ""),
      count = 390,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 5", displayName = ""),
      count = 386,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "POCO X3 NFC", displayName = ""),
      count = 381,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S10+", displayName = ""),
      count = 370,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S20+", displayName = ""),
      count = 357,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S21 Ultra 5G", displayName = ""),
      count = 354,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 3a", displayName = ""),
      count = 349,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S20", displayName = ""),
      count = 340,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S10e", displayName = ""),
      count = 338,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 4", displayName = ""),
      count = 331,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A71", displayName = ""),
      count = 329,
    ),
    WithCount(value = Device(manufacturer = "samsung", model = "", displayName = ""), count = 326),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A12", displayName = ""),
      count = 312,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "MI 8", displayName = ""),
      count = 275,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi A3", displayName = ""),
      count = 254,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "MI 9", displayName = ""),
      count = 252,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "HUAWEI P30 lite", displayName = ""),
      count = 251,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S9+", displayName = ""),
      count = 242,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi 9A", displayName = ""),
      count = 242,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi A1", displayName = ""),
      count = 240,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "Honor 10", displayName = ""),
      count = 240,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi 9 SE", displayName = ""),
      count = 239,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 8T", displayName = ""),
      count = 238,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "HUAWEI nova 5T", displayName = ""),
      count = 237,
    ),
    WithCount(
      value = Device(manufacturer = "OnePlus", model = "OnePlus 7 Pro", displayName = ""),
      count = 233,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A31", displayName = ""),
      count = 227,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi K20", displayName = ""),
      count = 227,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A52s 5G", displayName = ""),
      count = 213,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 3 XL", displayName = ""),
      count = 212,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 5a 5G", displayName = ""),
      count = 207,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 3", displayName = ""),
      count = 201,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi 9C NFC", displayName = ""),
      count = 200,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S21+ 5G", displayName = ""),
      count = 199,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy M31", displayName = ""),
      count = 197,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 10S", displayName = ""),
      count = 197,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S20 FE", displayName = ""),
      count = 194,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy Note9", displayName = ""),
      count = 191,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S21 FE 5G", displayName = ""),
      count = 184,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 4", displayName = ""),
      count = 182,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "HONOR 10i", displayName = ""),
      count = 179,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "POCO F3", displayName = ""),
      count = 169,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 6 Pro", displayName = ""),
      count = 166,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S20 Ultra 5G", displayName = ""),
      count = 161,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 8", displayName = ""),
      count = 160,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy Note10 Lite", displayName = ""),
      count = 158,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "Honor 8X", displayName = ""),
      count = 157,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A5(2017)", displayName = ""),
      count = 155,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi 5 Plus", displayName = ""),
      count = 154,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy Note8", displayName = ""),
      count = 152,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi A2", displayName = ""),
      count = 151,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "HUAWEI P30", displayName = ""),
      count = 147,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S7", displayName = ""),
      count = 143,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy Z Flip3 5G", displayName = ""),
      count = 143,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A10", displayName = ""),
      count = 143,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 2", displayName = ""),
      count = 143,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Xiaomi 11T", displayName = ""),
      count = 143,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "honor 10 Lite", displayName = ""),
      count = 142,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy S7 edge", displayName = ""),
      count = 141,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi 4X", displayName = ""),
      count = 140,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy Note20", displayName = ""),
      count = 139,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy M12", displayName = ""),
      count = 139,
    ),
    WithCount(
      value = Device(manufacturer = "OnePlus", model = "OnePlus5", displayName = ""),
      count = 139,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 4a (5G)", displayName = ""),
      count = 138,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "sdk_gphone_x86", displayName = ""),
      count = 136,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel 4 XL", displayName = ""),
      count = 136,
    ),
    WithCount(
      value = Device(manufacturer = "Google", model = "Pixel", displayName = ""),
      count = 132,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi A2 Lite", displayName = ""),
      count = 132,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "HONOR 20 PRO", displayName = ""),
      count = 131,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A72", displayName = ""),
      count = 130,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi 11 Lite", displayName = ""),
      count = 130,
    ),
    WithCount(
      value = Device(manufacturer = "HUAWEI", model = "P20 lite", displayName = ""),
      count = 130,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi Note 10", displayName = ""),
      count = 129,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "MI 8 Lite", displayName = ""),
      count = 127,
    ),
    WithCount(value = Device(manufacturer = "HUAWEI", model = "P20", displayName = ""), count = 25),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Redmi Note 10", displayName = ""),
      count = 22,
    ),
    WithCount(
      value = Device(manufacturer = "realme", model = "realme 6Pro", displayName = ""),
      count = 17,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy M21", displayName = ""),
      count = 16,
    ),
    WithCount(
      value = Device(manufacturer = "Xiaomi", model = "Mi 9T Pro", displayName = ""),
      count = 15,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A22", displayName = ""),
      count = 11,
    ),
    WithCount(
      value = Device(manufacturer = "samsung", model = "Galaxy A21s", displayName = ""),
      count = 10,
    ),
  )
