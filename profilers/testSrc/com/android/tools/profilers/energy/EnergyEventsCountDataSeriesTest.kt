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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS


class EnergyEventsCountDataSeriesTest {
  private val eventList = ProfilersTestData.generateEnergyEvents(PID)

  // Timestamp to expected event count map (unified pipeline).
  private val expectedEventCounts = mapOf(
    TimeUnit.SECONDS.toMicros(50) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(100) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(125) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(150) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(160) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(170) to mapOf("location" to 0, "wakelock" to 2, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(185) to mapOf("location" to 0, "wakelock" to 2, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(200) to mapOf("location" to 0, "wakelock" to 2, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(225) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(250) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(275) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(300) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(325) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(350) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(375) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(400) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(410) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(420) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(435) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(450) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(465) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(480) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(490) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(500) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(1000) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0))

  // Timestamp to expected event count map (legacy pipeline).
  // The start and end timestamps are treated differently: (start, end]. Thus we need a different map.
  private val expectedLegacyEventCounts = mapOf(
    TimeUnit.SECONDS.toMicros(50) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(100) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(125) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(150) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(160) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(170) to mapOf("location" to 0, "wakelock" to 2, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(185) to mapOf("location" to 0, "wakelock" to 2, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(200) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(225) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(250) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(275) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(300) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(325) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(350) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(375) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(400) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(410) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(420) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(435) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 2),
    TimeUnit.SECONDS.toMicros(450) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(465) to mapOf("location" to 0, "wakelock" to 1, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(480) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(490) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 1),
    TimeUnit.SECONDS.toMicros(500) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0),
    TimeUnit.SECONDS.toMicros(1000) to mapOf("location" to 0, "wakelock" to 0, "alarm_job" to 0))

  private val myEnergyService = FakeEnergyService(eventList = eventList)
  private val myTransportService = FakeTransportService(FakeTimer())

  @get:Rule
  val grpcChannel = FakeGrpcChannel("EnergyEventsDataSeriesTest", myTransportService, myEnergyService)
  private val myProfilerClient = ProfilerClient(grpcChannel.name)

  private val locationPredicate = { kind: EnergyDuration.Kind -> kind == EnergyDuration.Kind.LOCATION }
  private val wakelockPredicate = { kind: EnergyDuration.Kind -> kind == EnergyDuration.Kind.WAKE_LOCK }
  private val alarmAndJobPredicate = { kind: EnergyDuration.Kind -> kind == EnergyDuration.Kind.ALARM || kind == EnergyDuration.Kind.JOB }

  @Test
  fun testEventsCount() {
    eventList.forEach { event -> myTransportService.addEventToEventGroup(STREAM_ID, event) }
    val locationCountSeries = EnergyEventsCountDataSeries(myProfilerClient.transportClient, STREAM_ID, PID, locationPredicate)
    val wakeLockCountSeries = EnergyEventsCountDataSeries(myProfilerClient.transportClient, STREAM_ID, PID, wakelockPredicate)
    val alarmAndJobCountSeries = EnergyEventsCountDataSeries(myProfilerClient.transportClient, STREAM_ID, PID, alarmAndJobPredicate)
    for ((timestamp, countMap) in expectedEventCounts) {
      val range = Range(timestamp.toDouble(), timestamp.toDouble())
      assertWithMessage(String.format("Location count mismatch at timestamp: %d", timestamp)).that(
        locationCountSeries.getDataForXRange(range)[0].value).isEqualTo(countMap["location"])
      assertWithMessage(String.format("Wakelock count mismatch at timestamp: %d", timestamp)).that(
        wakeLockCountSeries.getDataForXRange(range)[0].value).isEqualTo(countMap["wakelock"])
      assertWithMessage(String.format("Alarm & job count mismatch at timestamp: %d", timestamp)).that(
        alarmAndJobCountSeries.getDataForXRange(range)[0].value).isEqualTo(countMap["alarm_job"])
    }
  }

  @Test
  fun testEventsCountLegacy() {
    val dataSeries = LegacyEnergyEventsDataSeries(myProfilerClient, ProfilersTestData.SESSION_DATA)
    val rangedSeries = RangedSeries(Range(0.0, Double.MAX_VALUE), dataSeries)
    val locationCountSeries = LegacyEnergyEventsCountDataSeries(rangedSeries, locationPredicate)
    val wakeLockCountSeries = LegacyEnergyEventsCountDataSeries(rangedSeries, wakelockPredicate)
    val alarmAndJobCountSeries = LegacyEnergyEventsCountDataSeries(rangedSeries, alarmAndJobPredicate)
    for ((timestamp, countMap) in expectedLegacyEventCounts) {
      val range = Range(timestamp.toDouble(), timestamp.toDouble())
      assertWithMessage(String.format("Location count mismatch at timestamp: %d", timestamp)).that(
        locationCountSeries.getDataForXRange(range)[0].value).isEqualTo(countMap["location"])
      assertWithMessage(String.format("Wakelock count mismatch at timestamp: %d", timestamp)).that(
        wakeLockCountSeries.getDataForXRange(range)[0].value).isEqualTo(countMap["wakelock"])
      assertWithMessage(String.format("Alarm & job count mismatch at timestamp: %d", timestamp)).that(
        alarmAndJobCountSeries.getDataForXRange(range)[0].value).isEqualTo(countMap["alarm_job"])
    }
  }

  companion object {
    const val STREAM_ID = 123L
    const val PID = 321
  }
}
