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
package com.android.tools.idea.ui.screenshot

import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension
import java.nio.file.Files

/** Tests for [DumpsysDisplayDeviceInfoParser]. */
class DumpsysDisplayDeviceInfoParserTest {

  @Test
  fun testPixelFold() {
    val displayInfo = DumpsysDisplayDeviceInfoParser.getActiveDisplays(getDumpsysOutput("PixelFold"))
    assertThat(displayInfo).containsExactly(
      DisplayDeviceInfo(0, 4619827677550801152, Dimension(2208, 1840), 0, 420, false)
    )
  }

  @Test
  fun testAutomotiveWithDistantDisplays() {
    val displayInfo = DumpsysDisplayDeviceInfoParser.getActiveDisplays(getDumpsysOutput("AutomotiveWithDistantDisplays"))
    assertThat(displayInfo).containsExactly(
      DisplayDeviceInfo(0, 4619827259835644672, Dimension(1080, 600), 0, 120, false),
      DisplayDeviceInfo(2, 4619827551948147201, Dimension(400, 600), 0, 120, false),
      DisplayDeviceInfo(3, 4619827124781842690, Dimension(3000, 600), 0, 120, false),
    )
  }

  @Test
  fun testRoundWatch() {
    val displayInfo = DumpsysDisplayDeviceInfoParser.getActiveDisplays(getDumpsysOutput("RoundWatch"))
    assertThat(displayInfo).containsExactly(
      DisplayDeviceInfo(0, 4619827259835644672, Dimension(454, 454), 0, 320, true)
    )
  }
}

private fun getDumpsysOutput(filename: String): String =
    Files.readString(resolveWorkspacePathUnchecked("tools/adt/idea/android-adb-ui/testData/dumpsys/$filename.txt"))
