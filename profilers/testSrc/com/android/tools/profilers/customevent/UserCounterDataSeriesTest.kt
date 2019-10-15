/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.customevent

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ObjectUtils.assertNotNull
import com.intellij.util.indexing.impl.DebugAssertions.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the UserCounterDataSeries that holds the event count for Custom Event Visualization.
 */
class UserCounterDataSeriesTest {

  private val timer = FakeTimer()
  private val ideProfilerServices = FakeIdeProfilerServices()

  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient("CustomEventMonitorTestChannel"), ideProfilerServices, timer)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  @Test
  fun testEmptyRange() {
    // Create a series with empty range.
    val series = UserCounterDataSeries(profilers.client.transportClient, profilers)
    val dataSeries = series.getDataForRange(Range())
    assertNotNull(dataSeries)
    // No data within given range.
    assertTrue(dataSeries.isEmpty())
  }

  @Test
  fun testNonEmptyRange() {
    // Create a series with a non-empty range.
    // Currently a set two values are returned for a non-empty range.
    val userCounterDataSeries = UserCounterDataSeries(profilers.client.transportClient, profilers)
    val dataSeriesForRange = userCounterDataSeries.getDataForRange(Range(10.0, 20.0))

    assertThat(dataSeriesForRange.size).isEqualTo(2)
    assertThat(dataSeriesForRange[0].x).isEqualTo(10L)
    assertThat(dataSeriesForRange[0].value).isEqualTo(0L)
    assertThat(dataSeriesForRange[1].x).isEqualTo(20L)
    assertThat(dataSeriesForRange[1].value).isEqualTo(0L)
  }
}


