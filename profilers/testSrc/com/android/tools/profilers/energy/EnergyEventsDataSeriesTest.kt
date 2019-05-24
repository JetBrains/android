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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.android.tools.profilers.ProfilerClient
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

  @Test
  fun testAllDataIncluded() {
    val dataSeries = EnergyEventsDataSeries(ProfilerClient(grpcChannel.name), ProfilersTestData.SESSION_DATA)

    val range = Range(0.0, Double.MAX_VALUE)
    val dataList = dataSeries.getDataForXRange(range)
    assertThat(dataList.map { it.value }).containsExactlyElementsIn(eventList)
  }

  @Test
  fun testEventsMerged() {
    val dataSeries = EnergyEventsDataSeries(ProfilerClient(grpcChannel.name), ProfilersTestData.SESSION_DATA)

    // Filter wakelocks
    run {
      val mergedSeries = MergedEnergyEventsDataSeries(dataSeries, EnergyDuration.Kind.WAKE_LOCK)
      val range = Range(0.0, Double.MAX_VALUE)
      val dataList = mergedSeries.getDataForXRange(range)
      assertThat(dataList).hasSize(4)

      val mergedWakeLocks = listOf(
        eventList.first { it.groupId == 1L },
        eventList.first { it.groupId == 3L && it.isEnded },
        eventList.first { it.groupId == 6L },
        eventList.first { it.groupId == 6L && it.isEnded }
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
        eventList.first { it.groupId == 2L },
        eventList.first { it.groupId == 2L && it.isEnded },
        eventList.first { it.groupId == 4L },
        eventList.first { it.groupId == 5L && it.isEnded }
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
        eventList.first { it.groupId == 1L },
        eventList.first { it.groupId == 2L && it.isEnded },
        eventList.first { it.groupId == 4L },
        eventList.first { it.groupId == 5L && it.isEnded }
      )

      assertThat(dataList.map { it.value }).containsExactlyElementsIn(mergedAllEvents)
    }
  }
}
