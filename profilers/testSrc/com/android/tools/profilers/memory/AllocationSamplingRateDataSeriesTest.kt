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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Arrays
import java.util.concurrent.TimeUnit

class AllocationSamplingRateDataSeriesTest {
  companion object {
    val TIMESTAMP1 = 1000L
    val TIMESTAMP2 = 2000L
    val TIMESTAMP3 = 3000L
    val SAMPLING_RATE1 = MemoryAllocSamplingData.newBuilder().apply { samplingNumInterval = 1 }.build()
    val SAMPLING_RATE2 = MemoryAllocSamplingData.newBuilder().apply { samplingNumInterval = 2 }.build()
    val SAMPLING_RATE3 = MemoryAllocSamplingData.newBuilder().apply { samplingNumInterval = 3 }.build()
  }

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer)
  private val myService = FakeMemoryService()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("AllocationSamplingRateDataSeriesTest", myTransportService, myService)

  @Test
  fun testGetDataForXRange() {
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
                                        ProfilersTestData.generateMemoryAllocSamplingData(
                                          ProfilersTestData.SESSION_DATA, TIMESTAMP1, SAMPLING_RATE1).build())
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
                                        ProfilersTestData.generateMemoryAllocSamplingData(
                                          ProfilersTestData.SESSION_DATA, TIMESTAMP2, SAMPLING_RATE2).build())
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
                                        ProfilersTestData.generateMemoryAllocSamplingData(
                                          ProfilersTestData.SESSION_DATA, TIMESTAMP3, SAMPLING_RATE3).build())

    val series = AllocationSamplingRateDataSeries(ProfilerClient(myGrpcChannel.channel), ProfilersTestData.SESSION_DATA)
    val dataList = series.getDataForRange(Range(0.0, java.lang.Double.MAX_VALUE))

    assertThat(dataList.size).isEqualTo(3)
    var data = dataList[0]
    assertThat(data.x).isEqualTo(1)
    assertThat(data.value.durationUs).isEqualTo(1)
    assertThat(data.value.previousRate).isNull()
    assertThat(data.value.currentRate.samplingNumInterval).isEqualTo(1)

    data = dataList[1]
    assertThat(data.x).isEqualTo(2)
    assertThat(data.value.durationUs).isEqualTo(1)
    assertThat(data.value.previousRate!!.samplingNumInterval).isEqualTo(1)
    assertThat(data.value.currentRate.samplingNumInterval).isEqualTo(2)

    data = dataList[2]
    assertThat(data.x).isEqualTo(3)
    assertThat(data.value.durationUs).isEqualTo(java.lang.Long.MAX_VALUE)
    assertThat(data.value.previousRate!!.samplingNumInterval).isEqualTo(2)
    assertThat(data.value.currentRate.samplingNumInterval).isEqualTo(3)
  }

  @Test
  fun testGetDataForXRangeNotReturnEventsBeforeRangeMin() {
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
                                        ProfilersTestData.generateMemoryAllocSamplingData(
                                          ProfilersTestData.SESSION_DATA, TIMESTAMP1, SAMPLING_RATE1).build())
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
                                        ProfilersTestData.generateMemoryAllocSamplingData(
                                          ProfilersTestData.SESSION_DATA, TIMESTAMP2, SAMPLING_RATE2).build())
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.streamId,
                                        ProfilersTestData.generateMemoryAllocSamplingData(
                                          ProfilersTestData.SESSION_DATA, TIMESTAMP3, SAMPLING_RATE3).build())

    val series = AllocationSamplingRateDataSeries(ProfilerClient(myGrpcChannel.channel), ProfilersTestData.SESSION_DATA)
    val dataList = series.getDataForRange(Range(4.0, java.lang.Double.MAX_VALUE))

    assertThat(dataList.size).isEqualTo(1)
    val data = dataList[0]
    assertThat(data.x).isEqualTo(TimeUnit.NANOSECONDS.toMicros(3000))
    assertThat(data.value.durationUs).isEqualTo(java.lang.Long.MAX_VALUE)

    // New pipeline correctly queries -1/+1 data. so here we don't get the event at t=2 as the previous rate event.
    assertThat(data.value.previousRate).isNull()
    assertThat(data.value.currentRate.samplingNumInterval).isEqualTo(3)
  }
}