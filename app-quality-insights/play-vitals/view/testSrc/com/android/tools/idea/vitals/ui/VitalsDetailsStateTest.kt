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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VitalsDetailsStateTest {

  @Test
  fun `construct console url`() {
    // url for default filters
    var state =
      VitalsDetailsState(
        null,
        TimeIntervalFilter.SEVEN_DAYS,
        emptySet(),
        ISSUE1,
        ConnectionMode.ONLINE,
        emptySet(),
        emptySet(),
        VisibilityType.ALL,
      )
    assertThat(state.toConsoleUrl()).isEqualTo("https://url.for-crash.com?days=7")

    // url for non empty filters
    state =
      VitalsDetailsState(
        null,
        TimeIntervalFilter.ONE_DAY,
        setOf(Version("version1"), Version("version2")),
        ISSUE1,
        ConnectionMode.ONLINE,
        setOf(
          OperatingSystemInfo("os1", "os1"),
          OperatingSystemInfo("os2", "os2"),
          OperatingSystemInfo("os3", "os3"),
        ),
        setOf(Device("Google", "Google/Pixel 5"), Device("Samsung", "Samsung/A32")),
        VisibilityType.USER_PERCEIVED,
      )

    assertThat(state.toConsoleUrl())
      .isEqualTo(
        "https://url.for-crash.com?days=1&versionCode=version1,version2&osVersion=os1,os2,os3&deviceName=Google/Pixel 5,Samsung/A32&appProcessState=Foreground"
      )
  }
}
