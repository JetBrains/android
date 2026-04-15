/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.cpu.config

import com.android.sdklib.AndroidVersion
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtMethodTraceOutputFormatTest {

  @Test
  fun testOutputVersionForPreCinnamonBun() {
    val device = Common.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.BAKLAVA).setArtVersionCode(373399999L).build()
    assertThat(getArtMethodTraceOutputVersion(device, true)).isEqualTo(1)
  }

  @Test
  fun testOutputVersionWhenEditorFlagDisabled() {
    val device = Common.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.CINNAMON_BUN).setArtVersionCode(373399999L).build()
    assertThat(getArtMethodTraceOutputVersion(device, false)).isEqualTo(1)
  }

  @Test
  fun testOutputVersionWhenArtVersionIsTooLow() {
    val device = Common.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.CINNAMON_BUN).setArtVersionCode(373399998L).build()
    assertThat(getArtMethodTraceOutputVersion(device, true)).isEqualTo(1)
  }

  @Test
  fun testOutputVersionReturnsTwo() {
    val device = Common.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.CINNAMON_BUN).setArtVersionCode(373399999L).build()
    assertThat(getArtMethodTraceOutputVersion(device, true)).isEqualTo(2)
  }
}
