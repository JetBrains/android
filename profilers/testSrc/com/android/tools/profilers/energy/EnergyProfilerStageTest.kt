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
import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
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
      .setTimestamp(2000)
      .setAlarmSet(EnergyProfiler.AlarmSet.getDefaultInstance())
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(3000)
      .setAlarmCancelled(EnergyProfiler.AlarmCancelled.getDefaultInstance())
      .setIsTerminal(true)
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(3000)
      .setJobScheduled(EnergyProfiler.JobScheduled.getDefaultInstance())
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(4000)
      .setJobFinished(EnergyProfiler.JobFinished.getDefaultInstance())
      .setIsTerminal(true)
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(0)
      .setWakeLockAcquired(EnergyProfiler.WakeLockAcquired.getDefaultInstance())
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(1000)
      .setWakeLockReleased(EnergyProfiler.WakeLockReleased.getDefaultInstance())
      .setIsTerminal(true)
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(4000)
      .setLocationUpdateRequested(EnergyProfiler.LocationUpdateRequested.getDefaultInstance())
      .build(),
    EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(5000)
      .setLocationUpdateRemoved(EnergyProfiler.LocationUpdateRemoved.getDefaultInstance())
      .setIsTerminal(true)
      .build()
  )

  private val profilerService = FakeProfilerService(true)

  @get:Rule
  var grpcChannel = FakeGrpcChannel("EnergyProfilerStageTest", profilerService, FakeEnergyService(eventList = fakeData))

  private lateinit var timer: FakeTimer
  private lateinit var myStage: EnergyProfilerStage

  @Before
  fun setUp() {
    timer = FakeTimer()
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    myStage = EnergyProfilerStage(StudioProfilers(grpcChannel.client, services, timer))
    myStage.studioProfilers.timeline.viewRange.set(TimeUnit.SECONDS.toMicros(0).toDouble(), TimeUnit.SECONDS.toMicros(5).toDouble())
    myStage.studioProfilers.stage = myStage
  }

  @Test
  fun getLegends() {
    val energyLegends = myStage.legends
    assertThat(energyLegends.cpuLegend.name).isEqualTo("CPU")
    assertThat(energyLegends.networkLegend.name).isEqualTo("NETWORK")

    assertThat(energyLegends.legends).hasSize(2)
  }

  @Test
  fun hasUserUsedSelection() {
    assertThat(myStage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(0f)
    assertThat(myStage.hasUserUsedEnergySelection()).isFalse()
    myStage.selectionModel.setSelectionEnabled(true)
    myStage.selectionModel.set(0.0, 100.0)
    assertThat(myStage.instructionsEaseOutModel.percentageComplete).isWithin(0f).of(1f);
    assertThat(myStage.hasUserUsedEnergySelection()).isTrue()
  }

  @Test
  fun setUsageTooltip() {
    myStage.enter()
    myStage.tooltip = EnergyUsageTooltip(myStage)
    assertThat(myStage.tooltip).isInstanceOf(EnergyUsageTooltip::class.java)
    val tooltip = myStage.tooltip as EnergyUsageTooltip
    assertThat(tooltip.legends.legends).hasSize(2)
    assertThat(tooltip.legends.cpuLegend.name).isEqualTo("CPU")
    assertThat(tooltip.legends.networkLegend.name).isEqualTo("NETWORK")
  }

  @Test
  fun setEventTooltip() {
    myStage.enter()
    myStage.tooltip = EnergyEventTooltip(myStage)
    assertThat(myStage.tooltip).isInstanceOf(EnergyEventTooltip::class.java)
    val tooltip = myStage.tooltip as EnergyEventTooltip
    assertThat(tooltip.legends.legends).hasSize(3)
    assertThat(tooltip.legends.locationLegend.name).isEqualTo("Location Event")
    assertThat(tooltip.legends.wakeLockLegend.name).isEqualTo("Wake Locks")
    assertThat(tooltip.legends.alarmAndJobLegend.name).isEqualTo("Alarms & Jobs")
  }

  fun getEventsModel() {
    val range = myStage.studioProfilers.timeline.viewRange
    val eventSeries = myStage.eventModel.series
    assertThat(eventSeries).hasSize(3)

    // Alarms & Jobs
    val alarmAndJobEvents = eventSeries[0].dataSeries.getDataForXRange(range)
    assertThat(alarmAndJobEvents).hasSize(4)
    for (i in 0..3) {
      assertThat(alarmAndJobEvents[i].value).isEqualTo(fakeData[i])
    }

    // Wake locks
    val wakeLockEvents = eventSeries[1].dataSeries.getDataForXRange(range)
    assertThat(wakeLockEvents).hasSize(2)
    for (i in 0..1) {
      assertThat(wakeLockEvents[i].value).isEqualTo(fakeData[i + 4])
    }

    // Locations
    val locationEvents = eventSeries[2].dataSeries.getDataForXRange(range)
    assertThat(locationEvents).hasSize(2)
    for (i in 0..1) {
      assertThat(locationEvents[i].value).isEqualTo(fakeData[i + 6])
    }
  }
}
