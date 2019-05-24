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
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.ArrayList
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS


class EnergyEventsCountDataSeriesTest {

  // W = Wake lock, J = Job
  //     |    |    |    |    |    |    |    |    |
  // 1:  W=========]
  // 2:       J==============]
  // 3:          W======]
  // 4:                           J=========]
  // 5:                                J=========]
  // 6:                                   W====]
  private val eventList =
    listOf(
      Common.Event.newBuilder()
        .setGroupId(1)
        .setTimestamp(SECONDS.toNanos(100))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(2)
        .setTimestamp(SECONDS.toNanos(150))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(3)
        .setTimestamp(SECONDS.toNanos(170))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(1)
        .setTimestamp(SECONDS.toNanos(200))
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(3)
        .setTimestamp(SECONDS.toNanos(250))
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(2)
        .setTimestamp(SECONDS.toNanos(300))
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(4)
        .setTimestamp(SECONDS.toNanos(350))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(5)
        .setTimestamp(SECONDS.toNanos(400))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(6)
        .setTimestamp(SECONDS.toNanos(420))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(4)
        .setTimestamp(SECONDS.toNanos(450))
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(6)
        .setTimestamp(SECONDS.toNanos(480))
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setGroupId(5)
        .setTimestamp(SECONDS.toNanos(500))
        .setIsEnded(true)
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .build()
    )

  private val service = FakeEnergyService(eventList = eventList)

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EnergyEventsDataSeriesTest", service)
  private val myProfilerClient = ProfilerClient(grpcChannel.name)

  @Test
  fun testAllDataIncluded() {
    val dataSeries = EnergyEventsDataSeries(myProfilerClient, ProfilersTestData.SESSION_DATA)

    val range = Range(0.0, Double.MAX_VALUE)
    val dataList = dataSeries.getDataForXRange(range)
    assertThat(dataList.map { it.value }).containsExactlyElementsIn(eventList)
  }

  @Test
  fun testEventsCount() {
    val dataSeries = EnergyEventsDataSeries(myProfilerClient, ProfilersTestData.SESSION_DATA)
    val rangedSeries = RangedSeries(Range(0.0, Double.MAX_VALUE), dataSeries)
    testEventsCount(EnergyEventsCountDataSeries(rangedSeries, EnergyDuration.Kind.LOCATION))
    testEventsCount(EnergyEventsCountDataSeries(rangedSeries, EnergyDuration.Kind.WAKE_LOCK))
    testEventsCount(EnergyEventsCountDataSeries(rangedSeries, EnergyDuration.Kind.ALARM, EnergyDuration.Kind.JOB))
  }

  private fun testEventsCount(countDataSeries: EnergyEventsCountDataSeries) {
    val eventTimestamps = eventList.stream().mapToLong { it.timestamp }.sorted().toArray()
    val testTimeStamps = ArrayList<Long>(eventList.size * 2 + 1)
    var lastTimestamp = 0L

    // Generate all possible timestamps to test
    eventTimestamps.forEach {
      testTimeStamps.add((lastTimestamp + it) / 2)
      testTimeStamps.add(it)
      lastTimestamp = it
    }
    testTimeStamps.add(eventTimestamps.last() * 2);
    testTimeStamps.forEach {
      val timestamp = it;
      // Calculate the number of events in a naive way.
      val count = eventList.count {
        val startEvent = it
        var result = false
        if (!startEvent.isEnded && countDataSeries.kindsFilter.contains(EnergyDuration.Kind.from(startEvent.energyEvent))) {
          val terminalEvent = eventList.last { it.groupId == startEvent.groupId && it.isEnded }
          result = timestamp >= startEvent.timestamp && timestamp < terminalEvent.timestamp
        }
        result
      }
      // Check if the results are matched
      val position = NANOSECONDS.toMicros(timestamp).toDouble()
      assertThat(countDataSeries.getDataForXRange(Range(position, position))[0].value).isEqualTo(count)
    }
  }
}
