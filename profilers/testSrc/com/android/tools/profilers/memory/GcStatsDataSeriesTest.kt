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
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for the GC duration data via the new data pipeline.
 */
class GcStatsDataSeriesTest {

  private val myTimer = FakeTimer()
  private val myService = FakeTransportService(myTimer)
  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("GcStatsDataSeriesTest", myService)
  private lateinit var myStage: MainMemoryProfilerStage

  @Before
  fun setup() {
    val ideServices = FakeIdeProfilerServices()
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), ideServices, myTimer)
    myStage = MainMemoryProfilerStage(profilers)

    // insert gc data for new pipeline.
    for (i in 0..9) {
      myService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
        // Space out the data by 1 second
                                 ProfilersTestData.generateMemoryGcData(
                                   ProfilersTestData.SESSION_DATA.pid,
                                   TimeUnit.SECONDS.toMicros(i.toLong()),
                                   Memory.MemoryGcData.newBuilder().setDuration(TimeUnit.MICROSECONDS.toNanos(i.toLong())).build())
                                   .build())
    }
  }

  @Test
  fun testGetData() {
    val model = myStage.gcStatsModel
    val viewRange = myStage.timeline.viewRange

    // Request full range
    viewRange.set(0.0, TimeUnit.SECONDS.toMicros(10).toDouble())
    var seriesData = model.series.series
    assertThat(seriesData.size).isEqualTo(10)
    for (i in seriesData.indices) {
      val data = seriesData[i]
      assertThat(data.x).isEqualTo(TimeUnit.SECONDS.toMicros(i.toLong()))
      assertThat(data.value.durationUs).isEqualTo(i)
    }

    // Request negative to mid range
    viewRange.set(TimeUnit.SECONDS.toMicros(-5).toDouble(), TimeUnit.SECONDS.toMicros(5).toDouble())
    seriesData = model.series.series
    // Should have samples from {0,6} (+1 sample)
    assertThat(seriesData.size).isEqualTo(7)
    for (i in seriesData.indices) {
      val data = seriesData[i]
      assertThat(data.x).isEqualTo(TimeUnit.SECONDS.toMicros(i.toLong()))
      assertThat(data.value.durationUs).isEqualTo(i)
    }

    // Request mid to high range
    viewRange.set(TimeUnit.SECONDS.toMicros(5).toDouble(), TimeUnit.SECONDS.toMicros(10).toDouble())
    seriesData = model.series.series
    // Should have samples from {4,9} (-1 sample)
    assertThat(seriesData.size).isEqualTo(6)
    // Should have sample starting from 4s.
    for (i in seriesData.indices) {
      val data = seriesData[i]
      assertThat(data.x).isEqualTo(TimeUnit.SECONDS.toMicros((i + 4).toLong()))
      assertThat(data.value.durationUs).isEqualTo(i + 4)
    }
  }
}
