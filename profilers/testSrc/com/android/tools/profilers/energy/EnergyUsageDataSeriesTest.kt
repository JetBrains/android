// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.EnergyProfiler.EnergySample
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit.SECONDS

class EnergyUsageDataSeriesTest {

  private val service = FakeEnergyService(
      listOf(
          EnergySample.newBuilder().setTimestamp(SECONDS.toNanos(5)).setCpuUsage(20).setNetworkUsage(30).build(),
          EnergySample.newBuilder().setTimestamp(SECONDS.toNanos(10)).setCpuUsage(20).setNetworkUsage(40).build(),
          EnergySample.newBuilder().setTimestamp(SECONDS.toNanos(15)).setCpuUsage(20).setNetworkUsage(50).build(),
          EnergySample.newBuilder().setTimestamp(SECONDS.toNanos(20)).setNetworkUsage(10).build()
      )
  )

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EnergyUsageDataSeriesTest", service)

  private lateinit var dataSeries: EnergyUsageDataSeries

  @Before
  fun setup() {
    dataSeries = EnergyUsageDataSeries(grpcChannel.client, ProfilersTestData.SESSION_DATA)
  }

  @Test
  fun testAllDataIncluded() {
    val range = Range(SECONDS.toMicros(1).toDouble(), SECONDS.toMicros(20).toDouble())
    val dataList = dataSeries.getDataForXRange(range)
    assertThat(dataList.size).isEqualTo(4)
    assertThat(dataList[0].value).isEqualTo(50)
    assertThat(dataList[1].value).isEqualTo(60)
    assertThat(dataList[2].value).isEqualTo(70)
    assertThat(dataList[3].value).isEqualTo(10)
  }

  @Test
  fun testExcludedTail() {
    val range = Range(SECONDS.toMicros(15).toDouble(), SECONDS.toMicros(19).toDouble())
    val dataList = dataSeries.getDataForXRange(range)
    assertThat(dataList.size).isEqualTo(1)
    assertThat(dataList[0].x).isEqualTo(SECONDS.toMicros(15))
    assertThat(dataList[0].value).isEqualTo(70)
  }

  @Test
  fun testExcludedHead() {
    val range = Range(SECONDS.toMicros(7).toDouble(), SECONDS.toMicros(10).toDouble())
    val dataList = dataSeries.getDataForXRange(range)
    assertThat(dataList.size).isEqualTo(1)
    assertThat(dataList[0].x).isEqualTo(SECONDS.toMicros(10))
    assertThat(dataList[0].value).isEqualTo(60)
  }
}
