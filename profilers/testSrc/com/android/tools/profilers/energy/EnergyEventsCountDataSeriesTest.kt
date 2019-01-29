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
import com.android.tools.profiler.proto.EnergyProfiler.*
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.*
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
      EnergyEvent.newBuilder()
        .setEventId(1)
        .setTimestamp(SECONDS.toNanos(100))
        .setWakeLockAcquired(WakeLockAcquired.getDefaultInstance())
        .build(),
      EnergyEvent.newBuilder()
        .setEventId(2)
        .setTimestamp(SECONDS.toNanos(150))
        .setJobStarted(JobStarted.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(3)
        .setTimestamp(SECONDS.toNanos(170))
        .setWakeLockAcquired(WakeLockAcquired.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(1)
        .setTimestamp(SECONDS.toNanos(200))
        .setIsTerminal(true)
        .setWakeLockReleased(WakeLockReleased.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(3)
        .setTimestamp(SECONDS.toNanos(250))
        .setIsTerminal(true)
        .setWakeLockReleased(WakeLockReleased.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(2)
        .setTimestamp(SECONDS.toNanos(300))
        .setIsTerminal(true)
        .setJobFinished(JobFinished.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(4)
        .setTimestamp(SECONDS.toNanos(350))
        .setJobStarted(JobStarted.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(5)
        .setTimestamp(SECONDS.toNanos(400))
        .setJobStarted(JobStarted.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(6)
        .setTimestamp(SECONDS.toNanos(420))
        .setWakeLockAcquired(WakeLockAcquired.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(4)
        .setTimestamp(SECONDS.toNanos(450))
        .setIsTerminal(true)
        .setJobFinished(JobFinished.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(6)
        .setTimestamp(SECONDS.toNanos(480))
        .setIsTerminal(true)
        .setWakeLockReleased(WakeLockReleased.getDefaultInstance()).build(),
      EnergyEvent.newBuilder()
        .setEventId(5)
        .setTimestamp(SECONDS.toNanos(500))
        .setIsTerminal(true)
        .setJobFinished(JobFinished.getDefaultInstance()).build()
    )

  private val service = FakeEnergyService(eventList = eventList)

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EnergyEventsDataSeriesTest", service)

  @Test
  fun testAllDataIncluded() {
    val dataSeries = EnergyEventsDataSeries(grpcChannel.client, ProfilersTestData.SESSION_DATA)

    val range = Range(0.0, Double.MAX_VALUE)
    val dataList = dataSeries.getDataForXRange(range)
    assertThat(dataList.map { it.value }).containsExactlyElementsIn(eventList)
  }

  @Test
  fun testEventsCount() {
    val dataSeries = EnergyEventsDataSeries(grpcChannel.client, ProfilersTestData.SESSION_DATA)
    val rangedSeries = RangedSeries(Range(0.0, Double.MAX_VALUE), dataSeries);
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
        if (!startEvent.isTerminal && countDataSeries.kindsFilter.contains(EnergyDuration.Kind.from(startEvent))) {
          val terminalEvent = eventList.last { it.eventId == startEvent.eventId && it.isTerminal }
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
