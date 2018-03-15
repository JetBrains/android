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
import com.android.tools.profiler.proto.EnergyProfiler.*
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit.SECONDS

class EnergyEventsDataSeriesTest {

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
  fun testEventsMerged() {
    val dataSeries = EnergyEventsDataSeries(grpcChannel.client, ProfilersTestData.SESSION_DATA)

    // Filter wakelocks
    run {
      val mergedSeries = MergedEnergyEventsDataSeries(dataSeries, EnergyDuration.Kind.WAKE_LOCK)
      val range = Range(0.0, Double.MAX_VALUE)
      val dataList = mergedSeries.getDataForXRange(range)
      assertThat(dataList).hasSize(4)

      val mergedWakeLocks = listOf(
        eventList.first { it.eventId == 1 },
        eventList.first { it.eventId == 3 && it.isTerminal },
        eventList.first { it.eventId == 6 },
        eventList.first { it.eventId == 6 && it.isTerminal }
      )

      assertThat(dataList.map { it.value }).containsExactlyElementsIn(mergedWakeLocks)
    }

    // Filter jobs
    run {
      val mergedSeries = MergedEnergyEventsDataSeries(dataSeries, EnergyDuration.Kind.JOB)
      val range = Range(0.0, Double.MAX_VALUE)
      val dataList = mergedSeries.getDataForXRange(range)
      assertThat(dataList).hasSize(4)

      val mergedJobs = listOf(
        eventList.first { it.eventId == 2 },
        eventList.first { it.eventId == 2 && it.isTerminal },
        eventList.first { it.eventId == 4 },
        eventList.first { it.eventId == 5 && it.isTerminal }
      )

      assertThat(dataList.map { it.value }).containsExactlyElementsIn(mergedJobs)
    }

    // Combine both jobs and wakelock events together
    run {
      val mergedSeries = MergedEnergyEventsDataSeries(dataSeries, EnergyDuration.Kind.WAKE_LOCK, EnergyDuration.Kind.JOB)
      val range = Range(0.0, Double.MAX_VALUE)
      val dataList = mergedSeries.getDataForXRange(range)
      assertThat(dataList).hasSize(4)

      val mergedAllEvents = listOf(
        eventList.first { it.eventId == 1 },
        eventList.first { it.eventId == 2 && it.isTerminal },
        eventList.first { it.eventId == 4 },
        eventList.first { it.eventId == 5 && it.isTerminal }
      )

      assertThat(dataList.map { it.value }).containsExactlyElementsIn(mergedAllEvents)
    }
  }
}
