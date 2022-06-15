/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorLiveMode
import org.junit.Test

class LiveModeStatisticsTest {

  @Test
  fun testStart() {
    val liveMode = LiveModeStatistics()
    liveMode.refreshButtonClicked()
    liveMode.selectionMade()
    liveMode.currentModeIsLive = true
    liveMode.selectionMade()
    liveMode.start()
    val data = DynamicLayoutInspectorLiveMode.newBuilder()
    liveMode.save { data }
    assertThat(data.refreshButtonClicks).isEqualTo(0)
    assertThat(data.clicksWithLiveUpdates).isEqualTo(0)
    assertThat(data.clicksWithoutLiveUpdates).isEqualTo(0)
  }

  @Test
  fun testToggleBackAndForth() {
    val liveMode = LiveModeStatistics()
    liveMode.start()
    liveMode.currentModeIsLive = true
    liveMode.selectionMade()
    liveMode.currentModeIsLive = false
    liveMode.refreshButtonClicked()
    liveMode.selectionMade()
    liveMode.selectionMade()
    liveMode.refreshButtonClicked()
    liveMode.selectionMade()
    liveMode.selectionMade()
    liveMode.currentModeIsLive = true
    liveMode.selectionMade()
    liveMode.selectionMade()
    val data = DynamicLayoutInspectorLiveMode.newBuilder()
    liveMode.save { data }
    assertThat(data.refreshButtonClicks).isEqualTo(2)
    assertThat(data.clicksWithLiveUpdates).isEqualTo(3)
    assertThat(data.clicksWithoutLiveUpdates).isEqualTo(4)
  }
}
