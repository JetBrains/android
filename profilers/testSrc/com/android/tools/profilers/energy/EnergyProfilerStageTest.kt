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
import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class EnergyProfilerStageTest {
  private val fakeData = ImmutableList.of<EnergyProfiler.EnergyEvent>(
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(1)
      .setTimestamp(2000)
      .setAlarmSet(EnergyProfiler.AlarmSet.getDefaultInstance())
      .setTraceId("alarmTraceId")
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(1)
      .setTimestamp(3000)
      .setAlarmCancelled(EnergyProfiler.AlarmCancelled.getDefaultInstance())
      .setIsTerminal(true)
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(2)
      .setTimestamp(3000)
      .setJobScheduled(EnergyProfiler.JobScheduled.getDefaultInstance())
      .setTraceId("jobTraceId")
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(2)
      .setTimestamp(4000)
      .setJobFinished(EnergyProfiler.JobFinished.getDefaultInstance())
      .setIsTerminal(true)
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(3)
      .setTimestamp(0)
      .setWakeLockAcquired(EnergyProfiler.WakeLockAcquired.getDefaultInstance())
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(3)
      .setTimestamp(1000)
      .setWakeLockReleased(EnergyProfiler.WakeLockReleased.getDefaultInstance())
      .setIsTerminal(true)
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(4)
      .setTimestamp(4000)
      .setLocationUpdateRequested(EnergyProfiler.LocationUpdateRequested.getDefaultInstance())
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(4)
      .setTimestamp(5000)
      .setLocationUpdateRemoved(EnergyProfiler.LocationUpdateRemoved.getDefaultInstance())
      .setIsTerminal(true)
      .build()
  )

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("EnergyProfilerStageTest", transportService, FakeProfilerService(timer),
                                    FakeEnergyService(eventList = fakeData))

  private lateinit var myStage: EnergyProfilerStage

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    myStage = EnergyProfilerStage(StudioProfilers(ProfilerClient(grpcChannel.name), services, timer))
    myStage.studioProfilers.timeline.viewRange.set(TimeUnit.SECONDS.toMicros(0).toDouble(), TimeUnit.SECONDS.toMicros(5).toDouble())
    myStage.studioProfilers.stage = myStage
    myStage.studioProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
  }

  @Test
  fun getLegends() {
    val energyLegends = myStage.legends
    assertThat(energyLegends.cpuLegend.name).isEqualTo("CPU")
    assertThat(energyLegends.networkLegend.name).isEqualTo("Network")
    assertThat(energyLegends.locationLegend.name).isEqualTo("Location")

    assertThat(energyLegends.legends).hasSize(3)
  }

  @Test
  fun hasUserUsedSelection() {
    assertThat(myStage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(0f)
    assertThat(myStage.hasUserUsedEnergySelection()).isFalse()
    myStage.selectionModel.setSelectionEnabled(true)
    myStage.selectionModel.set(0.0, 100.0)
    assertThat(myStage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(1f)
    assertThat(myStage.hasUserUsedEnergySelection()).isTrue()
  }

  @Test
  fun setUsageTooltip() {
    myStage.enter()
    myStage.tooltip = EnergyStageTooltip(myStage)
    assertThat(myStage.tooltip).isInstanceOf(EnergyStageTooltip::class.java)
    val tooltip = myStage.tooltip as EnergyStageTooltip
    assertThat(tooltip.usageLegends.legends).hasSize(3)
    assertThat(tooltip.usageLegends.cpuLegend.name).isEqualTo("CPU")
    assertThat(tooltip.usageLegends.networkLegend.name).isEqualTo("Network")
    assertThat(tooltip.usageLegends.locationLegend.name).isEqualTo("Location")
  }

  @Test
  fun setEventTooltip() {
    myStage.enter()
    myStage.tooltip = EnergyStageTooltip(myStage)
    assertThat(myStage.tooltip).isInstanceOf(EnergyStageTooltip::class.java)
    val tooltip = myStage.tooltip as EnergyStageTooltip
    assertThat(tooltip.eventLegends.legends).hasSize(3)
    assertThat(tooltip.eventLegends.locationLegend.name).isEqualTo("Location")
    assertThat(tooltip.eventLegends.wakeLockLegend.name).isEqualTo("Wake Locks")
    assertThat(tooltip.eventLegends.alarmAndJobLegend.name).isEqualTo("Alarms & Jobs")
  }

  @Test
  fun getEventsModel() {
    val range = myStage.studioProfilers.timeline.viewRange
    val eventSeries = myStage.eventModel.series
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
    assertThat(myStage.studioProfilers.selectedAppName).isEqualTo("FakeProcess")
    transportService.addFile("alarmTraceId", ByteString.copyFromUtf8("FakeProcess"))
    transportService.addFile("jobTraceId", ByteString.copyFromUtf8("ThirdParty"))
    val durationList = EnergyDuration.groupById(fakeData)

    myStage.eventOrigin = EnergyEventOrigin.APP_ONLY
    val appOnlyResult = myStage.filterByOrigin(durationList)
    assertThat(appOnlyResult.size).isEqualTo(1)
    assertThat(appOnlyResult[0].name).startsWith("Alarm")

    myStage.eventOrigin = EnergyEventOrigin.ALL
    val allResult = myStage.filterByOrigin(durationList)
    assertThat(allResult.size).isEqualTo(4)
    assertThat(allResult[0].name).startsWith("Alarm")
    assertThat(allResult[1].name).startsWith("Job")
    assertThat(allResult[2].name).startsWith("Wake Lock")
    assertThat(allResult[3].name).startsWith("Location")

    myStage.eventOrigin = EnergyEventOrigin.THIRD_PARTY_ONLY
    val thirdPartyResult = myStage.filterByOrigin(durationList)
    assertThat(thirdPartyResult.size).isEqualTo(1)
    assertThat(thirdPartyResult[0].name).startsWith("Job")
  }

  @Test
  fun selectedDurationFilteredByOrigin() {
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myStage.studioProfilers.selectedAppName).isEqualTo("FakeProcess")
    transportService.addFile("alarmTraceId", ByteString.copyFromUtf8("FakeProcess"))
    transportService.addFile("jobTraceId", ByteString.copyFromUtf8("ThirdParty"))
    val durationList = EnergyDuration.groupById(fakeData)

    myStage.eventOrigin = EnergyEventOrigin.ALL
    myStage.selectedDuration = durationList[0]
    assertThat(myStage.selectedDuration).isEqualTo(durationList[0])

    myStage.eventOrigin = EnergyEventOrigin.THIRD_PARTY_ONLY
    assertThat(myStage.selectedDuration).isNull()
    myStage.selectedDuration = durationList[0] // Ignored because filter is set
    assertThat(myStage.selectedDuration).isNull()
    myStage.selectedDuration = durationList[1]
    assertThat(myStage.selectedDuration).isEqualTo(durationList[1])
    myStage.selectedDuration = null
    assertThat(myStage.getSelectedDuration()).isNull()

    myStage.eventOrigin = EnergyEventOrigin.APP_ONLY
    myStage.selectedDuration = durationList[0]
    assertThat(myStage.selectedDuration).isEqualTo(durationList[0])
    myStage.selectedDuration = durationList[1]
    assertThat(myStage.selectedDuration).isEqualTo(durationList[0])
    myStage.selectedDuration = null
    assertThat(myStage.getSelectedDuration()).isNull()
  }

  @Test
  fun setSelectedDurationTracksEnergyEventMetadata() {
    val featureTracker = myStage.studioProfilers.ideServices.featureTracker as FakeFeatureTracker
    assertThat(featureTracker.lastEnergyEventMetadata).isNull()

    val energyDuration = EnergyDuration.groupById(fakeData)[0]!!
    myStage.selectedDuration = energyDuration

    val energyEventMetadata = featureTracker.lastEnergyEventMetadata!!
    assertThat(energyDuration.eventList).isNotEmpty()
    for ((i, event) in energyDuration.eventList.withIndex()) {
      assertThat(energyEventMetadata.subevents[i]).isEqualTo(event)
    }

    myStage.selectedDuration = null
    // Setting the selected duration shouldn't track anything, so we shouldn't change the last EnergyEventMetadata tracked.
    assertThat(featureTracker.lastEnergyEventMetadata).isEqualTo(energyEventMetadata)
  }

  @Test
  fun eventsIntersectingWithCreatedSelectionRangeShouldBeTracked() {
    val featureTracker = myStage.studioProfilers.ideServices.featureTracker as FakeFeatureTracker
    assertThat(featureTracker.lastEnergyRangeMetadata).isNull()
    myStage.selectionModel.setSelectionEnabled(true)

    // Setting a range that doesn't contain any events shouldn't track anything.
    myStage.selectionModel.set(50000.0, 100000.0)
    assertThat(featureTracker.lastEnergyRangeMetadata).isNull()

    // Clear the range to make sure selectionCreated() will be called next time we set the range.
    myStage.selectionModel.clear()
    // Set the range [500ns, 1500ns], which should return a single EnergyEvent (wake lock) from fakeData that happened at 1000ns
    myStage.selectionModel.set(0.5, 1.5)
    val energyRangeMetadata = featureTracker.lastEnergyRangeMetadata!!
    assertThat(energyRangeMetadata.eventCounts).hasSize(1)
    val eventCount = energyRangeMetadata.eventCounts[0]
    assertThat(eventCount.kind).isEqualTo(EnergyDuration.Kind.WAKE_LOCK)
    assertThat(eventCount.count).isEqualTo(1)
  }
}
