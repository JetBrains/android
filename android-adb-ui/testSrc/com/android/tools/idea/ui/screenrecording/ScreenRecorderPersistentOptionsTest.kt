/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension

/**
 * Tests for [ScreenRecorderPersistentOptions]
 *
 * Copied from com.android.tools.idea.ddms.screenrecord.ScreenRecorderPersistentOptionsTest
 */
internal class ScreenRecorderPersistentOptionsTest {
  @Test
  fun toScreenRecorderOptions_defaultValues() {
    val options = ScreenRecorderPersistentOptions().toScreenRecorderOptions(size = null, timeLimitSec = 300)

    assertThat(options.bitrateMbps).isEqualTo(4)
    assertThat(options.showTouches).isFalse()
    assertThat(options.height).isEqualTo(0)
    assertThat(options.width).isEqualTo(0)
    assertThat(options.timeLimitSec).isEqualTo(300)
  }

  @Test
  fun toScreenRecorderOptions_setBitrate() {
    val options = ScreenRecorderPersistentOptions().apply { bitRateMbps = 10 }.toScreenRecorderOptions(size = null, timeLimitSec = 0)

    assertThat(options.bitrateMbps).isEqualTo(10)
  }

  @Test
  fun toScreenRecorderOptions_setBShowTaps() {
    val options = ScreenRecorderPersistentOptions().apply { showTaps = true }.toScreenRecorderOptions(size = null, timeLimitSec = 0)

    assertThat(options.showTouches).isTrue()
  }

  @Test
  fun toScreenRecorderOptions_withSize_defaultResolution() {
    val options = ScreenRecorderPersistentOptions().toScreenRecorderOptions(Dimension(1000, 2000), timeLimitSec = 0)

    // Don't specify size if 100%, just record at native resolution.
    assertThat(options.height).isEqualTo(0)
    assertThat(options.width).isEqualTo(0)
  }

  @Test
  fun toScreenRecorderOptions_withSize_roundsTo16() {
    val options = ScreenRecorderPersistentOptions().apply { resolutionPercent = 25 }
        .toScreenRecorderOptions(Dimension(1000, 2000), timeLimitSec = 0)

    assertThat(options.width).isEqualTo(256)
    assertThat(options.height).isEqualTo(496)
  }
}