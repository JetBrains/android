/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.energy

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class EnergyProfilerStageTest {
  private val fakeData = ImmutableList.of(
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(2000)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(
        Energy.EnergyEventData.newBuilder()
          .setAlarmSet(Energy.AlarmSet.getDefaultInstance())
          .setCallstack("FakeProcess alarm callstack"))
      .build(),
    Common.Event.newBuilder()
      .setGroupId(1)
      .setTimestamp(3000)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmCancelled(Energy.AlarmCancelled.getDefaultInstance()))
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(2)
      .setTimestamp(3000)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(
        Energy.EnergyEventData.newBuilder()
          .setJobScheduled(Energy.JobScheduled.getDefaultInstance())
          .setCallstack("ThirdParty job callstack"))
      .build(),
    Common.Event.newBuilder()
      .setGroupId(2)
      .setTimestamp(4000)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(
        Energy.EnergyEventData.newBuilder()
          .setJobFinished(Energy.JobFinished.getDefaultInstance()))
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(3)
      .setTimestamp(0)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
      .build(),
    Common.Event.newBuilder()
      .setGroupId(3)
      .setTimestamp(1000)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
      .setIsEnded(true)
      .build(),
    Common.Event.newBuilder()
      .setGroupId(4)
      .setTimestamp(4000)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationUpdateRequested(Energy.LocationUpdateRequested.getDefaultInstance()))
      .build(),
    Common.Event.newBuilder()
      .setGroupId(4)
      .setTimestamp(5000)
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationUpdateRemoved(Energy.LocationUpdateRemoved.getDefaultInstance()))
      .setIsEnded(true)
      .build()
  )

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("EnergyProfilerStageTest", transportService)

  private lateinit var stage: EnergyProfilerStage

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply {
      enableEnergyProfiler(true)
    }
    fakeData.forEach { event -> transportService.addEventToStream(1, event) }
    stage = EnergyProfilerStage(StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer))
    stage.timeline.viewRange.set(TimeUnit.SECONDS.toMicros(0).toDouble(), TimeUnit.SECONDS.toMicros(5).toDouble())
    stage.studioProfilers.stage = stage
    stage.studioProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
  }

  @Test
  fun getLegends() {
    val energyLegends = stage.legends
    assertThat(energyLegends.cpuLegend.name).isEqualTo("CPU")
    assertThat(energyLegends.networkLegend.name).isEqualTo("Network")
    assertThat(energyLegends.locationLegend.name).isEqualTo("Location")

    assertThat(energyLegends.legends).hasSize(3)
  }

  @Test
  fun hasUserUsedSelection() {
    assertThat(stage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(0f)
    assertThat(stage.hasUserUsedEnergySelection()).isFalse()
    stage.rangeSelectionModel.setSelectionEnabled(true)
    stage.rangeSelectionModel.set(0.0, 100.0)
    assertThat(stage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(1f)
    assertThat(stage.hasUserUsedEnergySelection()).isTrue()
  }

  @Test
  fun setUsageTooltip() {
    stage.enter()
    stage.tooltip = EnergyStageTooltip(stage)
    assertThat(stage.tooltip).isInstanceOf(EnergyStageTooltip::class.java)
    val tooltip = stage.tooltip as EnergyStageTooltip
    assertThat(tooltip.usageLegends.legends).hasSize(3)
    assertThat(tooltip.usageLegends.cpuLegend.name).isEqualTo("CPU")
    assertThat(tooltip.usageLegends.networkLegend.name).isEqualTo("Network")
    assertThat(tooltip.usageLegends.locationLegend.name).isEqualTo("Location")
  }

  @Test
  fun getEventsModel() {
    val range = stage.timeline.viewRange
    val eventSeries = stage.eventModel.series
    assertThat(eventSeries).hasSize(3)

    // Alarms & Jobs
    val alarmAndJobEvents = eventSeries[0].getSeriesForRange(range)
    assertThat(alarmAndJobEvents).hasSize(4)
    for (i in 0..3) {
      assertThat(alarmAndJobEvents[i].value).isEqualTo(fakeData[i])
    }

    // Wake locks
    val wakeLockEvents = eventSeries[1].getSeriesForRange(range)
    assertThat(wakeLockEvents).hasSize(2)
    for (i in 0..1) {
      assertThat(wakeLockEvents[i].value).isEqualTo(fakeData[i + 4])
    }

    // Locations
    val locationEvents = eventSeries[2].getSeriesForRange(range)
    assertThat(locationEvents).hasSize(2)
    for (i in 0..1) {
      assertThat(locationEvents[i].value).isEqualTo(fakeData[i + 6])
    }
  }

  @Test
  fun filterEventsByConfiguration() {
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(stage.studioProfilers.selectedAppName).isEqualTo("FakeProcess")
    val durationList = EnergyDuration.groupById(fakeData)

    stage.eventOrigin = EnergyEventOrigin.APP_ONLY
    val appOnlyResult = stage.filterByOrigin(durationList)
    assertThat(appOnlyResult.size).isEqualTo(1)
    assertThat(appOnlyResult[0].name).startsWith("Alarm")

    stage.eventOrigin = EnergyEventOrigin.ALL
    val allResult = stage.filterByOrigin(durationList)
    assertThat(allResult.size).isEqualTo(4)
    assertThat(allResult[0].name).startsWith("Alarm")
    assertThat(allResult[1].name).startsWith("Job")
    assertThat(allResult[2].name).startsWith("Wake Lock")
    assertThat(allResult[3].name).startsWith("Location")

    stage.eventOrigin = EnergyEventOrigin.THIRD_PARTY_ONLY
    val thirdPartyResult = stage.filterByOrigin(durationList)
    assertThat(thirdPartyResult.size).isEqualTo(1)
    assertThat(thirdPartyResult[0].name).startsWith("Job")
  }

  @Test
  fun selectedDurationFilteredByOrigin() {
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(stage.studioProfilers.selectedAppName).isEqualTo("FakeProcess")
    val durationList = EnergyDuration.groupById(fakeData)

    stage.eventOrigin = EnergyEventOrigin.ALL
    stage.selectedDuration = durationList[0]
    assertThat(stage.selectedDuration).isEqualTo(durationList[0])

    stage.eventOrigin = EnergyEventOrigin.THIRD_PARTY_ONLY
    assertThat(stage.selectedDuration).isNull()
    stage.selectedDuration = durationList[0] // Ignored because filter is set
    assertThat(stage.selectedDuration).isNull()
    stage.selectedDuration = durationList[1]
    assertThat(stage.selectedDuration).isEqualTo(durationList[1])
    stage.selectedDuration = null
    assertThat(stage.getSelectedDuration()).isNull()

    stage.eventOrigin = EnergyEventOrigin.APP_ONLY
    stage.selectedDuration = durationList[0]
    assertThat(stage.selectedDuration).isEqualTo(durationList[0])
    stage.selectedDuration = durationList[1]
    assertThat(stage.selectedDuration).isEqualTo(durationList[0])
    stage.selectedDuration = null
    assertThat(stage.getSelectedDuration()).isNull()
  }

  @Test
  fun setSelectedDurationTracksEnergyEventMetadata() {
    val featureTracker = stage.studioProfilers.ideServices.featureTracker as FakeFeatureTracker
    assertThat(featureTracker.lastEnergyEventMetadata).isNull()

    val energyDuration = EnergyDuration.groupById(fakeData)[0]!!
    stage.selectedDuration = energyDuration

    val energyEventMetadata = featureTracker.lastEnergyEventMetadata!!
    assertThat(energyDuration.eventList).isNotEmpty()
    for ((i, event) in energyDuration.eventList.withIndex()) {
      assertThat(energyEventMetadata.subevents[i]).isEqualTo(event)
    }

    stage.selectedDuration = null
    // Setting the selected duration shouldn't track anything, so we shouldn't change the last EnergyEventMetadata tracked.
    assertThat(featureTracker.lastEnergyEventMetadata).isEqualTo(energyEventMetadata)
  }

  @Test
  fun eventsIntersectingWithCreatedSelectionRangeShouldBeTracked() {
    val featureTracker = stage.studioProfilers.ideServices.featureTracker as FakeFeatureTracker
    assertThat(featureTracker.lastEnergyRangeMetadata).isNull()
    stage.rangeSelectionModel.setSelectionEnabled(true)

    // Setting a range that doesn't contain any events shouldn't track anything.
    stage.rangeSelectionModel.set(50000.0, 100000.0)
    assertThat(featureTracker.lastEnergyRangeMetadata).isNull()

    // Clear the range to make sure selectionCreated() will be called next time we set the range.
    stage.rangeSelectionModel.clear()
    // Set the range [500ns, 1500ns], which should return a single EnergyEvent (wake lock) from fakeData that happened at 1000ns
    stage.rangeSelectionModel.set(0.5, 1.5)
    val energyRangeMetadata = featureTracker.lastEnergyRangeMetadata!!
    assertThat(energyRangeMetadata.eventCounts).hasSize(1)
    val eventCount = energyRangeMetadata.eventCounts[0]
    assertThat(eventCount.kind).isEqualTo(EnergyDuration.Kind.WAKE_LOCK)
    assertThat(eventCount.count).isEqualTo(1)
  }
}
