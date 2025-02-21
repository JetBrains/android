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
package com.android.tools.idea.ui.screenrecording

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [EmulatorConsoleRecordingProvider].
 */
class EmulatorConsoleRecordingProviderTest {
  @Test
  fun getRecorderOptions() {
    val options = ScreenRecorderOptions(
        displayId = PRIMARY_DISPLAY_ID, width = 600, height = 400, bitrateMbps = 6, showTouches = true, timeLimitSec = 300)

    val args = EmulatorConsoleRecordingProvider.getRecorderOptions(options)

    assertThat(args).asList().containsExactly(
      "--size",
      "600x400",
      "--bit-rate",
      "6000000",
      "--time-limit",
      "300",
    ).inOrder()
  }

  @Test
  fun getRecorderOptionsDefaultTimeLimit() {
    val options = ScreenRecorderOptions(
        displayId = PRIMARY_DISPLAY_ID, width = 600, height = 400, bitrateMbps = 6, showTouches = true, timeLimitSec = 0)

    val args = EmulatorConsoleRecordingProvider.getRecorderOptions(options)

    assertThat(args).asList().containsExactly(
      "--size",
      "600x400",
      "--bit-rate",
      "6000000",
    ).inOrder()
  }
}