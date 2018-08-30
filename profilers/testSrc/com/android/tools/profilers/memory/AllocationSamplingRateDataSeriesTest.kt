/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.ProfilersTestData
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class AllocationSamplingRateDataSeriesTest {

  private val myService = FakeMemoryService()

  @Rule
  var myGrpcChannel = FakeGrpcChannel("AllocationSamplingRateDataSeriesTest", myService)

  @Test
  fun testGetDataForXRange() {
    val memoryData = MemoryProfiler.MemoryData.newBuilder()
      .setEndTimestamp(1)
      .addAllocSamplingRateEvents(MemoryProfiler.AllocationSamplingRateEvent.newBuilder().setTimestamp(1000).setSamplingRate(
        MemoryProfiler.AllocationSamplingRate.newBuilder().setSamplingNumInterval(1).build()
      ))
      .addAllocSamplingRateEvents(MemoryProfiler.AllocationSamplingRateEvent.newBuilder().setTimestamp(2000).setSamplingRate(
        MemoryProfiler.AllocationSamplingRate.newBuilder().setSamplingNumInterval(2).build()
      ))
      .addAllocSamplingRateEvents(MemoryProfiler.AllocationSamplingRateEvent.newBuilder().setTimestamp(3000).setSamplingRate(
        MemoryProfiler.AllocationSamplingRate.newBuilder().setSamplingNumInterval(3).build()
      ))
      .build()
    myService.setMemoryData(memoryData)

    val series = AllocationSamplingRateDataSeries(myGrpcChannel.client.memoryClient, ProfilersTestData.SESSION_DATA)
    val dataList = series.getDataForXRange(Range(0.0, java.lang.Double.MAX_VALUE))

    assertThat(dataList.size).isEqualTo(3)
    var data = dataList[0]
    assertThat(data.x).isEqualTo(1)
    assertThat(data.value.durationUs).isEqualTo(1)
    assertThat(data.value.oldRateEvent.samplingRate.samplingNumInterval).isEqualTo(1)
    assertThat(data.value.newRateEvent.samplingRate.samplingNumInterval).isEqualTo(2)

    data = dataList[1]
    assertThat(data.x).isEqualTo(2)
    assertThat(data.value.durationUs).isEqualTo(1)
    assertThat(data.value.oldRateEvent.samplingRate.samplingNumInterval).isEqualTo(2)
    assertThat(data.value.newRateEvent.samplingRate.samplingNumInterval).isEqualTo(3)

    data = dataList[2]
    assertThat(data.x).isEqualTo(3)
    assertThat(data.value.durationUs).isEqualTo(TimeUnit.NANOSECONDS.toMicros(java.lang.Long.MAX_VALUE - 3000))
    assertThat(data.value.oldRateEvent.samplingRate.samplingNumInterval).isEqualTo(3)
    assertThat(data.value.newRateEvent.samplingRate.samplingNumInterval).isEqualTo(3)
  }
}